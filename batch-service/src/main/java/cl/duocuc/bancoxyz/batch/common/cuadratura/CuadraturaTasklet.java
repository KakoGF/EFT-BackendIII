package cl.duocuc.bancoxyz.batch.common.cuadratura;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import cl.duocuc.bancoxyz.batch.common.util.ArchivoCsvUtil;

public class CuadraturaTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CuadraturaTasklet.class);

    private final String nombreJob;
    private final Resource archivo;
    private final JdbcTemplate jdbcTemplate;
    private final String tablaValidos;
    private final String tablaAnomalias;
    private final String tablaOmitidos;

    public CuadraturaTasklet(String nombreJob, Resource archivo, JdbcTemplate jdbcTemplate,
            String tablaValidos, String tablaAnomalias, String tablaOmitidos) {
        this.nombreJob = nombreJob;
        this.archivo = archivo;
        this.jdbcTemplate = jdbcTemplate;
        this.tablaValidos = tablaValidos;
        this.tablaAnomalias = tablaAnomalias;
        this.tablaOmitidos = tablaOmitidos;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long registrosArchivo = ArchivoCsvUtil.contarRegistros(archivo);
        long validos = contar(tablaValidos);
        long anomalias = contar(tablaAnomalias);
        long omitidos = contar(tablaOmitidos);
        long totalBaseDatos = validos + anomalias + omitidos;
        boolean cuadra = totalBaseDatos == registrosArchivo;

        jdbcTemplate.update(
                "INSERT INTO cuadratura_proceso (job, archivo, fecha_proceso, registros_archivo, validos, anomalias, omitidos, cuadra) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                nombreJob, archivo.getFilename(), LocalDateTime.now(), registrosArchivo, validos, anomalias, omitidos, cuadra);

        log.info("+----------------------------------------------------------------+");
        log.info("| CUADRATURA {} ({})", nombreJob, archivo.getFilename());
        log.info("|   Registros en el archivo legacy : {}", registrosArchivo);
        log.info("|   Validos guardados               : {}", validos);
        log.info("|   Anomalias de negocio            : {}", anomalias);
        log.info("|   Omitidos con su motivo          : {}", omitidos);
        log.info("|   Total en base de datos          : {}", totalBaseDatos);
        log.info("|   Resultado                       : {}", cuadra ? "CUADRA, no se perdio ningun registro" : "NO CUADRA");
        log.info("+----------------------------------------------------------------+");

        if (!cuadra) {
            log.warn("[CUADRATURA] Diferencia de {} registros entre el archivo y la base de datos",
                    registrosArchivo - totalBaseDatos);
        }
        return RepeatStatus.FINISHED;
    }

    private long contar(String tabla) {
        if (tabla == null) {
            return 0;
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
        return total == null ? 0 : total;
    }
}
