package cl.duocuc.bancoxyz.batch.transacciones;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
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
import cl.duocuc.bancoxyz.batch.transacciones.model.TransaccionProcesada;
import cl.duocuc.bancoxyz.batch.transacciones.model.TransaccionRaw;
import cl.duocuc.bancoxyz.batch.transacciones.processor.TransaccionItemProcessor;
import cl.duocuc.bancoxyz.batch.transacciones.processor.TransaccionSkipListener;

@Configuration
public class TransaccionJobConfig {

    private static final Logger log = LoggerFactory.getLogger(TransaccionJobConfig.class);
    private static final String ARCHIVO = "movimientos_financieros_diarios.csv";

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

    private Resource archivoTransacciones() {
        return new FileSystemResource(Path.of(dataDir, ARCHIVO));
    }

    @Bean
    @StepScope
    public FlatFileItemReader<TransaccionRaw> transaccionItemReaderParticionado(
            @Value("#{stepExecutionContext['startItem']}") Integer startItem,
            @Value("#{stepExecutionContext['endItem']}") Integer endItem) {

        FlatFileItemReader<TransaccionRaw> reader = new FlatFileItemReaderBuilder<TransaccionRaw>()
                .name("transaccionItemReaderParticionado")
                .resource(archivoTransacciones())
                .linesToSkip(1)
                .delimited()
                .names("id", "fecha", "monto", "tipo")
                .targetType(TransaccionRaw.class)
                .strict(true)
                .build();
        reader.setCurrentItemCount(startItem - 1);
        reader.setMaxItemCount(endItem);
        return reader;
    }

    @Bean
    public Partitioner transaccionPartitioner() {
        return new ArchivoCsvRangePartitioner(archivoTransacciones());
    }

    @Bean
    public TaskExecutorPartitionHandler transaccionPartitionHandler(
            @Qualifier("minionStepTransacciones") Step minionStepTransacciones,
            @Qualifier("batchTaskExecutor") TaskExecutor batchTaskExecutor) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(minionStepTransacciones);
        handler.setTaskExecutor(batchTaskExecutor);
        handler.setGridSize(gridSize);
        return handler;
    }

    @Bean
    public JdbcBatchItemWriter<TransaccionProcesada> transaccionValidaWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<TransaccionProcesada>()
                .dataSource(dataSource)
                .sql("INSERT INTO transaccion (id, fecha, monto, tipo) VALUES (:id, :fecha, :monto, :tipo)")
                .beanMapped()
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<TransaccionProcesada> transaccionAnomaliaWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<TransaccionProcesada>()
                .dataSource(dataSource)
                .sql("INSERT INTO transaccion_anomalia (id_origen, fecha_origen, monto_origen, tipo_origen, motivo, fecha_deteccion) "
                        + "VALUES (:id, :fecha, :monto, :tipo, :motivoAnomalia, CURRENT_DATE)")
                .beanMapped()
                .build();
    }

    @Bean
    public ItemWriter<TransaccionProcesada> transaccionCompositeWriter(
            JdbcBatchItemWriter<TransaccionProcesada> transaccionValidaWriter,
            JdbcBatchItemWriter<TransaccionProcesada> transaccionAnomaliaWriter,
            SimulacionFallas simulacionFallas) {

        Classifier<TransaccionProcesada, ItemWriter<? super TransaccionProcesada>> classifier =
                item -> item.isAnomalia() ? transaccionAnomaliaWriter : transaccionValidaWriter;

        ClassifierCompositeItemWriter<TransaccionProcesada> compositeWriter = new ClassifierCompositeItemWriter<>();
        compositeWriter.setClassifier(classifier);
        return new EscrituraConSimulacionWriter<>(compositeWriter, simulacionFallas);
    }

    @Bean
    public Step stepProcesarTransacciones(JobRepository jobRepository,
                                           Partitioner transaccionPartitioner,
                                           TaskExecutorPartitionHandler transaccionPartitionHandler) {
        return new StepBuilder("stepProcesarTransacciones", jobRepository)
                .partitioner("minionStepTransacciones", transaccionPartitioner)
                .partitionHandler(transaccionPartitionHandler)
                .build();
    }

    @Bean
    public Step minionStepTransacciones(JobRepository jobRepository,
                                         PlatformTransactionManager transactionManager,
                                         ItemStreamReader<TransaccionRaw> transaccionItemReaderParticionado,
                                         TransaccionItemProcessor transaccionItemProcessor,
                                         ItemWriter<TransaccionProcesada> transaccionCompositeWriter,
                                         JdbcTemplate jdbcTemplate) {

        return new StepBuilder("minionStepTransacciones", jobRepository)
                .<TransaccionRaw, TransaccionProcesada>chunk(chunkSize, transactionManager)
                .reader(transaccionItemReaderParticionado)
                .processor(transaccionItemProcessor)
                .writer(transaccionCompositeWriter)
                .faultTolerant()
                .retryPolicy(PoliticaReintentos.crear(maxReintentos))
                .backOffPolicy(PoliticaReintentos.esperaCreciente())
                .skipPolicy(new DatosInconsistentesSkipPolicy(skipLimit))
                .listener(new ReintentoListener())
                .listener(new TransaccionSkipListener(jdbcTemplate))
                .listener(new PerformanceStepListener())
                .build();
    }

    @Bean
    public Step stepCuadraturaTransacciones(JobRepository jobRepository,
                                             PlatformTransactionManager transactionManager,
                                             JdbcTemplate jdbcTemplate) {
        CuadraturaTasklet cuadratura = new CuadraturaTasklet("Job 1 - Transacciones Diarias", archivoTransacciones(),
                jdbcTemplate, "transaccion", "transaccion_anomalia", "transaccion_error_lectura");
        return new StepBuilder("stepCuadraturaTransacciones", jobRepository)
                .tasklet(cuadratura, transactionManager)
                .build();
    }

    @Bean
    public Step stepAlertaCalidadTransacciones(JobRepository jobRepository,
                                                PlatformTransactionManager transactionManager) {
        Tasklet alertaTasklet = (contribution, chunkContext) -> {
            log.warn("!!! ALERTA DE CALIDAD DE DATOS !!! El Step stepProcesarTransacciones omitio una proporcion "
                    + "de registros sobre el umbral ({}). Revisar {} antes de confiar en el resumen.",
                    umbralAlertaCalidad, ARCHIVO);
            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("stepAlertaCalidadTransacciones", jobRepository)
                .tasklet(alertaTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step stepResumenTransacciones(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          JdbcTemplate jdbcTemplate) {
        Tasklet resumenTasklet = (contribution, chunkContext) -> {
            List<Map<String, Object>> porTipo = jdbcTemplate.queryForList(
                    "SELECT tipo, COUNT(*) AS total, COALESCE(SUM(monto),0) AS suma FROM transaccion GROUP BY tipo ORDER BY tipo");
            List<Map<String, Object>> validas = jdbcTemplate.queryForList(
                    "SELECT COUNT(*) AS total, COALESCE(SUM(monto),0) AS suma FROM transaccion");
            List<Map<String, Object>> anomaliasPorMotivo = jdbcTemplate.queryForList(
                    "SELECT motivo, COUNT(*) AS total FROM transaccion_anomalia GROUP BY motivo ORDER BY total DESC");
            Long totalAnomalias = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaccion_anomalia", Long.class);
            Long totalErrores = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaccion_error_lectura", Long.class);

            long totalValidas = ((Number) validas.get(0).get("total")).longValue();
            BigDecimal sumaMontos = (BigDecimal) validas.get(0).get("suma");

            jdbcTemplate.update(
                    "INSERT INTO resumen_transaccion_diaria (fecha_proceso, total_transacciones_validas, monto_total, total_anomalias, total_omitidos) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    LocalDate.now(), totalValidas, sumaMontos, totalAnomalias, totalErrores);

            log.info("###################################################################");
            log.info("# RESUMEN REPORTE DE TRANSACCIONES DIARIAS");
            log.info("# Transacciones validas        : {} (monto total {})", totalValidas, sumaMontos);
            porTipo.forEach(fila -> log.info("#    {} : {} transacciones por {}",
                    fila.get("tipo"), fila.get("total"), fila.get("suma")));
            log.info("# Anomalias detectadas         : {}", totalAnomalias);
            anomaliasPorMotivo.forEach(fila -> log.info("#    {} : {}", fila.get("motivo"), fila.get("total")));
            log.info("# Registros omitidos (sin dato recuperable): {}", totalErrores);
            MotivosUtil.contarPorCodigo(jdbcTemplate, "transaccion_error_lectura")
                    .forEach((codigo, total) -> log.info("#    {} : {}", codigo, total));
            log.info("###################################################################");

            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("stepResumenTransacciones", jobRepository)
                .tasklet(resumenTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step stepLimpiezaTransacciones(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          JdbcTemplate jdbcTemplate) {
        Tasklet limpiezaTasklet = (contribution, chunkContext) -> {
            jdbcTemplate.execute("DELETE FROM resumen_transaccion_diaria");
            jdbcTemplate.execute("DELETE FROM transaccion_error_lectura");
            jdbcTemplate.execute("DELETE FROM transaccion_anomalia");
            jdbcTemplate.execute("DELETE FROM transaccion");
            log.info(">>> Limpieza previa completada: tablas del Job 1 vaciadas para una corrida nueva.");
            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("stepLimpiezaTransacciones", jobRepository)
                .tasklet(limpiezaTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job jobReporteTransaccionesDiarias(JobRepository jobRepository,
                                               Step stepLimpiezaTransacciones,
                                               Step stepProcesarTransacciones,
                                               Step stepCuadraturaTransacciones,
                                               Step stepAlertaCalidadTransacciones,
                                               Step stepResumenTransacciones) {

        CalidadDatosDecider decider = new CalidadDatosDecider("stepProcesarTransacciones", umbralAlertaCalidad);

        return new JobBuilder("jobReporteTransaccionesDiarias", jobRepository)
                .listener(new JobLoggingListener("Reporte de Transacciones Diarias"))
                .start(stepLimpiezaTransacciones)
                .next(stepProcesarTransacciones)
                .next(stepCuadraturaTransacciones)
                .next(decider)
                    .on("CALIDAD_CRITICA").to(stepAlertaCalidadTransacciones)
                        .next(stepResumenTransacciones)
                .from(decider)
                    .on("*").to(stepResumenTransacciones)
                .end()
                .build();
    }
}
