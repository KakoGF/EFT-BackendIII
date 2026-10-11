package cl.duocuc.bancoxyz.batch.cuentasanuales;

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
import org.springframework.batch.item.support.ClassifierCompositeItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.classify.Classifier;
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
import cl.duocuc.bancoxyz.batch.cuentasanuales.model.CuentaAnualProcesada;
import cl.duocuc.bancoxyz.batch.cuentasanuales.model.CuentaAnualRaw;
import cl.duocuc.bancoxyz.batch.cuentasanuales.processor.CuentaAnualItemProcessor;
import cl.duocuc.bancoxyz.batch.cuentasanuales.processor.CuentaAnualSkipListener;

@Configuration
public class CuentaAnualJobConfig {

    private static final Logger log = LoggerFactory.getLogger(CuentaAnualJobConfig.class);
    private static final String ARCHIVO = "estados_financieros_anuales.csv";

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

    private Resource archivoEstados() {
        return new FileSystemResource(Path.of(dataDir, ARCHIVO));
    }

    @Bean
    @StepScope
    public FlatFileItemReader<CuentaAnualRaw> cuentaAnualItemReaderParticionado(
            @Value("#{stepExecutionContext['startItem']}") Integer startItem,
            @Value("#{stepExecutionContext['endItem']}") Integer endItem) {

        FlatFileItemReader<CuentaAnualRaw> reader = new FlatFileItemReaderBuilder<CuentaAnualRaw>()
                .name("cuentaAnualItemReaderParticionado")
                .resource(archivoEstados())
                .linesToSkip(1)
                .delimited()
                .names("cuentaId", "fecha", "transaccion", "monto", "descripcion")
                .targetType(CuentaAnualRaw.class)
                .strict(true)
                .build();
        reader.setCurrentItemCount(startItem - 1);
        reader.setMaxItemCount(endItem);
        return reader;
    }

    @Bean
    public Partitioner cuentaAnualPartitioner() {
        return new ArchivoCsvRangePartitioner(archivoEstados());
    }

    @Bean
    public TaskExecutorPartitionHandler cuentaAnualPartitionHandler(
            @Qualifier("minionStepCuentasAnuales") Step minionStepCuentasAnuales,
            @Qualifier("batchTaskExecutor") TaskExecutor batchTaskExecutor) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(minionStepCuentasAnuales);
        handler.setTaskExecutor(batchTaskExecutor);
        handler.setGridSize(gridSize);
        return handler;
    }

    @Bean
    public JdbcBatchItemWriter<CuentaAnualProcesada> cuentaAnualDetalleWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<CuentaAnualProcesada>()
                .dataSource(dataSource)
                .sql("INSERT INTO cuenta_anual_detalle (cuenta_id, fecha, transaccion, monto, descripcion) "
                        + "VALUES (:cuentaId, :fecha, :transaccion, :monto, :descripcion)")
                .beanMapped()
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<CuentaAnualProcesada> cuentaAnualAnomaliaWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<CuentaAnualProcesada>()
                .dataSource(dataSource)
                .sql("INSERT INTO cuenta_anual_anomalia (cuenta_id, fecha, transaccion, monto, descripcion, motivo, fecha_deteccion) "
                        + "VALUES (:cuentaId, :fecha, :transaccion, :monto, :descripcion, :motivoAnomalia, CURRENT_DATE)")
                .beanMapped()
                .build();
    }

    @Bean
    public ItemWriter<CuentaAnualProcesada> cuentaAnualCompositeWriter(
            JdbcBatchItemWriter<CuentaAnualProcesada> cuentaAnualDetalleWriter,
            JdbcBatchItemWriter<CuentaAnualProcesada> cuentaAnualAnomaliaWriter,
            SimulacionFallas simulacionFallas) {

        Classifier<CuentaAnualProcesada, ItemWriter<? super CuentaAnualProcesada>> classifier =
                item -> item.isAnomalia() ? cuentaAnualAnomaliaWriter : cuentaAnualDetalleWriter;

        ClassifierCompositeItemWriter<CuentaAnualProcesada> compositeWriter = new ClassifierCompositeItemWriter<>();
        compositeWriter.setClassifier(classifier);
        return new EscrituraConSimulacionWriter<>(compositeWriter, simulacionFallas);
    }

    @Bean
    public Step stepProcesarCuentasAnuales(JobRepository jobRepository,
                                            Partitioner cuentaAnualPartitioner,
                                            TaskExecutorPartitionHandler cuentaAnualPartitionHandler) {
        return new StepBuilder("stepProcesarCuentasAnuales", jobRepository)
                .partitioner("minionStepCuentasAnuales", cuentaAnualPartitioner)
                .partitionHandler(cuentaAnualPartitionHandler)
                .build();
    }

    @Bean
    public Step minionStepCuentasAnuales(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          ItemStreamReader<CuentaAnualRaw> cuentaAnualItemReaderParticionado,
                                          CuentaAnualItemProcessor cuentaAnualItemProcessor,
                                          ItemWriter<CuentaAnualProcesada> cuentaAnualCompositeWriter,
                                          JdbcTemplate jdbcTemplate) {

        return new StepBuilder("minionStepCuentasAnuales", jobRepository)
                .<CuentaAnualRaw, CuentaAnualProcesada>chunk(chunkSize, transactionManager)
                .reader(cuentaAnualItemReaderParticionado)
                .processor(cuentaAnualItemProcessor)
                .writer(cuentaAnualCompositeWriter)
                .faultTolerant()
                .retryPolicy(PoliticaReintentos.crear(maxReintentos))
                .backOffPolicy(PoliticaReintentos.esperaCreciente())
                .skipPolicy(new DatosInconsistentesSkipPolicy(skipLimit))
                .listener(new ReintentoListener())
                .listener(new CuentaAnualSkipListener(jdbcTemplate))
                .listener(new PerformanceStepListener())
                .build();
    }

    @Bean
    public Step stepCuadraturaCuentasAnuales(JobRepository jobRepository,
                                              PlatformTransactionManager transactionManager,
                                              JdbcTemplate jdbcTemplate) {
        CuadraturaTasklet cuadratura = new CuadraturaTasklet("Job 3 - Estados de Cuenta Anuales", archivoEstados(),
                jdbcTemplate, "cuenta_anual_detalle", "cuenta_anual_anomalia", "cuenta_anual_error_lectura");
        return new StepBuilder("stepCuadraturaCuentasAnuales", jobRepository)
                .tasklet(cuadratura, transactionManager)
                .build();
    }

    @Bean
    public Step stepAlertaCalidadCuentasAnuales(JobRepository jobRepository,
                                                 PlatformTransactionManager transactionManager) {
        Tasklet alertaTasklet = (contribution, chunkContext) -> {
            log.warn("!!! ALERTA DE CALIDAD DE DATOS !!! El Step stepProcesarCuentasAnuales omitio una proporcion "
                    + "de registros sobre el umbral ({}). Revisar {} antes de usar el informe de auditoria.",
                    umbralAlertaCalidad, ARCHIVO);
            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("stepAlertaCalidadCuentasAnuales", jobRepository)
                .tasklet(alertaTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step stepResumenCuentasAnuales(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager,
                                           JdbcTemplate jdbcTemplate) {
        Tasklet resumenTasklet = (contribution, chunkContext) -> {
            jdbcTemplate.update("DELETE FROM cuenta_anual_resumen");
            jdbcTemplate.update(
                    "INSERT INTO cuenta_anual_resumen (cuenta_id, total_movimientos, total_abonos, total_cargos, saldo_neto_anual) "
                            + "SELECT cuenta_id, COUNT(*), "
                            + "SUM(CASE WHEN transaccion = 'deposito' THEN monto ELSE 0 END), "
                            + "SUM(CASE WHEN transaccion <> 'deposito' THEN monto ELSE 0 END), "
                            + "SUM(CASE WHEN transaccion = 'deposito' THEN monto ELSE -monto END) "
                            + "FROM cuenta_anual_detalle GROUP BY cuenta_id");

            List<Map<String, Object>> resumen = jdbcTemplate.queryForList(
                    "SELECT cuenta_id, total_movimientos, total_abonos, total_cargos, saldo_neto_anual "
                            + "FROM cuenta_anual_resumen ORDER BY cuenta_id");
            List<Map<String, Object>> anomaliasPorMotivo = jdbcTemplate.queryForList(
                    "SELECT motivo, COUNT(*) AS total FROM cuenta_anual_anomalia GROUP BY motivo ORDER BY total DESC");
            Long errores = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cuenta_anual_error_lectura", Long.class);

            log.info("###################################################################");
            log.info("# INFORME ANUAL DE CUENTAS (para auditoria)");
            resumen.forEach(fila -> log.info("#   Cuenta {}: {} movimientos | abonos {} | cargos {} | saldo neto {}",
                    fila.get("cuenta_id"), fila.get("total_movimientos"), fila.get("total_abonos"),
                    fila.get("total_cargos"), fila.get("saldo_neto_anual")));
            log.info("# Movimientos anomalos (guardados aparte, no suman al saldo):");
            anomaliasPorMotivo.forEach(fila -> log.info("#    {} : {}", fila.get("motivo"), fila.get("total")));
            log.info("# Registros omitidos: {}", errores);
            MotivosUtil.contarPorCodigo(jdbcTemplate, "cuenta_anual_error_lectura")
                    .forEach((codigo, total) -> log.info("#    {} : {}", codigo, total));
            log.info("###################################################################");

            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("stepResumenCuentasAnuales", jobRepository)
                .tasklet(resumenTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step stepLimpiezaCuentasAnuales(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager,
                                           JdbcTemplate jdbcTemplate) {
        Tasklet limpiezaTasklet = (contribution, chunkContext) -> {
            jdbcTemplate.execute("DELETE FROM cuenta_anual_resumen");
            jdbcTemplate.execute("DELETE FROM cuenta_anual_error_lectura");
            jdbcTemplate.execute("DELETE FROM cuenta_anual_anomalia");
            jdbcTemplate.execute("DELETE FROM cuenta_anual_detalle");
            log.info(">>> Limpieza previa completada: tablas del Job 3 vaciadas para una corrida nueva.");
            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("stepLimpiezaCuentasAnuales", jobRepository)
                .tasklet(limpiezaTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job jobGeneracionEstadosCuentaAnuales(JobRepository jobRepository,
                                                  Step stepLimpiezaCuentasAnuales,
                                                  Step stepProcesarCuentasAnuales,
                                                  Step stepCuadraturaCuentasAnuales,
                                                  Step stepAlertaCalidadCuentasAnuales,
                                                  Step stepResumenCuentasAnuales) {

        CalidadDatosDecider decider = new CalidadDatosDecider("stepProcesarCuentasAnuales", umbralAlertaCalidad);

        return new JobBuilder("jobGeneracionEstadosCuentaAnuales", jobRepository)
                .listener(new JobLoggingListener("Generacion de Estados de Cuenta Anuales"))
                .start(stepLimpiezaCuentasAnuales)
                .next(stepProcesarCuentasAnuales)
                .next(stepCuadraturaCuentasAnuales)
                .next(decider)
                    .on("CALIDAD_CRITICA").to(stepAlertaCalidadCuentasAnuales)
                        .next(stepResumenCuentasAnuales)
                .from(decider)
                    .on("*").to(stepResumenCuentasAnuales)
                .end()
                .build();
    }
}
