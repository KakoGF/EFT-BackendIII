package cl.duocuc.bancoxyz.batch.intereses;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import cl.duocuc.bancoxyz.batch.common.cuadratura.CuadraturaTasklet;
import cl.duocuc.bancoxyz.batch.common.decider.CalidadDatosDecider;
import cl.duocuc.bancoxyz.batch.common.listener.JobLoggingListener;
import cl.duocuc.bancoxyz.batch.common.listener.PerformanceStepListener;
import cl.duocuc.bancoxyz.batch.common.partition.ArchivoCsvRangePartitioner;
import cl.duocuc.bancoxyz.batch.common.policy.DatosInconsistentesSkipPolicy;
import cl.duocuc.bancoxyz.batch.common.policy.PoliticaReintentos;
import cl.duocuc.bancoxyz.batch.common.policy.ReintentoListener;
import cl.duocuc.bancoxyz.batch.common.simulacion.EscrituraConSimulacionWriter;
import cl.duocuc.bancoxyz.batch.common.simulacion.SimulacionFallas;
import cl.duocuc.bancoxyz.batch.common.util.MotivosUtil;
import cl.duocuc.bancoxyz.batch.intereses.model.CuentaInteresProcesada;
import cl.duocuc.bancoxyz.batch.intereses.model.CuentaInteresRaw;
import cl.duocuc.bancoxyz.batch.intereses.processor.InteresItemProcessor;
import cl.duocuc.bancoxyz.batch.intereses.processor.InteresSkipListener;

@Configuration
public class InteresJobConfig {

    private static final Logger log = LoggerFactory.getLogger(InteresJobConfig.class);
    private static final String ARCHIVO = "intereses_trimestrales.csv";

    @Value("${app.batch.data-dir}")
    private String dataDir;

    @Value("${app.batch.chunk-size:50}")
    private int chunkSize;

    @Value("${app.batch.skip-limit:400}")
    private int skipLimit;

    @Value("${app.batch.grid-size:3}")
    private int gridSize;

    @Value("${app.batch.reintento.max-intentos:3}")
    private int maxReintentos;

    @Value("${app.batch.umbral-alerta-calidad:0.3}")
    private double umbralAlertaCalidad;

    private Resource archivoIntereses() {
        return new FileSystemResource(Path.of(dataDir, ARCHIVO));
    }

    @Bean
    @StepScope
    public FlatFileItemReader<CuentaInteresRaw> interesItemReaderParticionado(
            @Value("#{stepExecutionContext['startItem']}") Integer startItem,
            @Value("#{stepExecutionContext['endItem']}") Integer endItem) {

        FlatFileItemReader<CuentaInteresRaw> reader = new FlatFileItemReaderBuilder<CuentaInteresRaw>()
                .name("interesItemReaderParticionado")
                .resource(archivoIntereses())
                .linesToSkip(1)
                .delimited()
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .targetType(CuentaInteresRaw.class)
                .strict(true)
                .build();
        reader.setCurrentItemCount(startItem - 1);
        reader.setMaxItemCount(endItem);
        return reader;
    }

    @Bean
    public Partitioner interesPartitioner() {
        return new ArchivoCsvRangePartitioner(archivoIntereses());
    }

    @Bean
    public TaskExecutorPartitionHandler interesPartitionHandler(
            @Qualifier("minionStepIntereses") Step minionStepIntereses,
            @Qualifier("batchTaskExecutor") TaskExecutor batchTaskExecutor) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(minionStepIntereses);
        handler.setTaskExecutor(batchTaskExecutor);
        handler.setGridSize(gridSize);
        return handler;
    }

    @Bean
    public JdbcBatchItemWriter<CuentaInteresProcesada> cuentaInteresJdbcWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<CuentaInteresProcesada>()
                .dataSource(dataSource)
                .sql("INSERT INTO cuenta_interes (cuenta_id, nombre, saldo_inicial, edad, tipo, tasa_aplicada, interes_mensual, saldo_final, fecha_calculo) "
                        + "VALUES (:cuentaId, :nombre, :saldoInicial, :edad, :tipo, :tasaAplicada, :interesMensual, :saldoFinal, CURRENT_DATE)")
                .beanMapped()
                .build();
    }

    @Bean
    public ItemWriter<CuentaInteresProcesada> cuentaInteresWriter(
            JdbcBatchItemWriter<CuentaInteresProcesada> cuentaInteresJdbcWriter,
            SimulacionFallas simulacionFallas) {
        return new EscrituraConSimulacionWriter<>(cuentaInteresJdbcWriter, simulacionFallas);
    }

    @Bean
    public Step stepCalcularIntereses(JobRepository jobRepository,
                                       Partitioner interesPartitioner,
                                       TaskExecutorPartitionHandler interesPartitionHandler) {
        return new StepBuilder("stepCalcularIntereses", jobRepository)
                .partitioner("minionStepIntereses", interesPartitioner)
                .partitionHandler(interesPartitionHandler)
                .build();
    }

    @Bean
    public Step minionStepIntereses(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager,
                                     ItemStreamReader<CuentaInteresRaw> interesItemReaderParticionado,
                                     InteresItemProcessor interesItemProcessor,
                                     ItemWriter<CuentaInteresProcesada> cuentaInteresWriter,
                                     JdbcTemplate jdbcTemplate) {

        return new StepBuilder("minionStepIntereses", jobRepository)
                .<CuentaInteresRaw, CuentaInteresProcesada>chunk(chunkSize, transactionManager)
                .reader(interesItemReaderParticionado)
                .processor(interesItemProcessor)
                .writer(cuentaInteresWriter)
                .faultTolerant()
                .retryPolicy(PoliticaReintentos.crear(maxReintentos))
                .backOffPolicy(PoliticaReintentos.esperaCreciente())
                .skipPolicy(new DatosInconsistentesSkipPolicy(skipLimit))
                .listener(new ReintentoListener())
                .listener(new InteresSkipListener(jdbcTemplate))
                .listener(new PerformanceStepListener())
                .build();
    }

    @Bean
    public Step stepCuadraturaIntereses(JobRepository jobRepository,
                                         PlatformTransactionManager transactionManager,
                                         JdbcTemplate jdbcTemplate) {
        CuadraturaTasklet cuadratura = new CuadraturaTasklet("Job 2 - Intereses Mensuales", archivoIntereses(),
                jdbcTemplate, "cuenta_interes", null, "interes_anomalia");
        return new StepBuilder("stepCuadraturaIntereses", jobRepository)
                .tasklet(cuadratura, transactionManager)
                .build();
    }

    @Bean
    public Step stepAlertaCalidadIntereses(JobRepository jobRepository,
                                            PlatformTransactionManager transactionManager) {
        Tasklet alertaTasklet = (contribution, chunkContext) -> {
            log.warn("!!! ALERTA DE CALIDAD DE DATOS !!! El Step stepCalcularIntereses omitio una proporcion "
                    + "de registros sobre el umbral ({}). Revisar {} antes de confiar en el calculo.",
                    umbralAlertaCalidad, ARCHIVO);
            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("stepAlertaCalidadIntereses", jobRepository)
                .tasklet(alertaTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step stepResumenIntereses(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      JdbcTemplate jdbcTemplate) {
        Tasklet resumenTasklet = (contribution, chunkContext) -> {
            List<Map<String, Object>> porTipo = jdbcTemplate.queryForList(
                    "SELECT tipo, COUNT(*) AS cantidad, COALESCE(SUM(saldo_inicial),0) AS saldo_inicial, "
                            + "COALESCE(SUM(interes_mensual),0) AS interes, COALESCE(SUM(saldo_final),0) AS saldo_final "
                            + "FROM cuenta_interes GROUP BY tipo ORDER BY tipo");
            Long omitidas = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM interes_anomalia", Long.class);

            log.info("###################################################################");
            log.info("# RESUMEN CALCULO DE INTERESES MENSUALES");
            porTipo.forEach(fila -> log.info("#   {}: {} registros | saldo inicial {} | interes del mes {} | saldo final {}",
                    fila.get("tipo"), fila.get("cantidad"), fila.get("saldo_inicial"), fila.get("interes"),
                    fila.get("saldo_final")));
            log.info("# Registros omitidos por datos inconsistentes: {}", omitidas);
            MotivosUtil.contarPorCodigo(jdbcTemplate, "interes_anomalia")
                    .forEach((codigo, total) -> log.info("#    {} : {}", codigo, total));
            log.info("###################################################################");

            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("stepResumenIntereses", jobRepository)
                .tasklet(resumenTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step stepLimpiezaIntereses(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      JdbcTemplate jdbcTemplate) {
        Tasklet limpiezaTasklet = (contribution, chunkContext) -> {
            jdbcTemplate.execute("DELETE FROM interes_anomalia");
            jdbcTemplate.execute("DELETE FROM cuenta_interes");
            log.info(">>> Limpieza previa completada: tablas del Job 2 vaciadas para una corrida nueva.");
            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("stepLimpiezaIntereses", jobRepository)
                .tasklet(limpiezaTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job jobCalculoInteresesMensuales(JobRepository jobRepository,
                                             Step stepLimpiezaIntereses,
                                             Step stepCalcularIntereses,
                                             Step stepCuadraturaIntereses,
                                             Step stepAlertaCalidadIntereses,
                                             Step stepResumenIntereses) {

        CalidadDatosDecider decider = new CalidadDatosDecider("stepCalcularIntereses", umbralAlertaCalidad);

        return new JobBuilder("jobCalculoInteresesMensuales", jobRepository)
                .listener(new JobLoggingListener("Calculo de Intereses Mensuales"))
                .start(stepLimpiezaIntereses)
                .next(stepCalcularIntereses)
                .next(stepCuadraturaIntereses)
                .next(decider)
                    .on("CALIDAD_CRITICA").to(stepAlertaCalidadIntereses)
                        .next(stepResumenIntereses)
                .from(decider)
                    .on("*").to(stepResumenIntereses)
                .end()
                .build();
    }
}
