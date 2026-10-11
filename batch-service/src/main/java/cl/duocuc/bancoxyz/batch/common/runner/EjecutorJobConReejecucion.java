package cl.duocuc.bancoxyz.batch.common.runner;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

@Component
public class EjecutorJobConReejecucion implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(EjecutorJobConReejecucion.class);
    private static final DateTimeFormatter FORMATO_CORRIDA = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JobLauncher jobLauncher;
    private final Map<String, Job> jobs;
    private final String nombreJob;
    private final String corrida;
    private final int maxIntentos;
    private final long esperaMs;

    private int codigoSalida = 0;

    public EjecutorJobConReejecucion(
            JobLauncher jobLauncher,
            Map<String, Job> jobs,
            @Value("${spring.batch.job.name}") String nombreJob,
            @Value("${corrida:${app.batch.corrida:}}") String corrida,
            @Value("${app.batch.reejecucion.max-intentos:3}") int maxIntentos,
            @Value("${app.batch.reejecucion.espera-ms:5000}") long esperaMs) {
        this.jobLauncher = jobLauncher;
        this.jobs = jobs;
        this.nombreJob = nombreJob;
        this.corrida = corrida;
        this.maxIntentos = maxIntentos;
        this.esperaMs = esperaMs;
    }

    @Override
    public void run(String... args) throws Exception {
        Job job = jobs.get(nombreJob);
        if (job == null) {
            log.error("No existe el job '{}'. Jobs disponibles: {}", nombreJob, jobs.keySet());
            codigoSalida = 2;
            return;
        }

        String identificadorCorrida = corrida == null || corrida.isBlank()
                ? LocalDateTime.now().format(FORMATO_CORRIDA)
                : corrida.trim();
        JobParameters parametros = new JobParametersBuilder()
                .addString("corrida", identificadorCorrida)
                .toJobParameters();

        for (int intento = 1; intento <= maxIntentos; intento++) {
            log.info("[REEJECUCION] Lanzando {} (corrida {}), intento {} de {}",
                    nombreJob, identificadorCorrida, intento, maxIntentos);
            try {
                JobExecution ejecucion = jobLauncher.run(job, parametros);
                if (ejecucion.getStatus() == BatchStatus.COMPLETED) {
                    log.info("[REEJECUCION] {} termino COMPLETED en el intento {}", nombreJob, intento);
                    codigoSalida = 0;
                    return;
                }
                log.warn("[REEJECUCION] {} termino {} en el intento {}: falla critica, se reejecuta la misma instancia",
                        nombreJob, ejecucion.getStatus(), intento);
            } catch (JobInstanceAlreadyCompleteException ex) {
                log.info("[REEJECUCION] La corrida {} de {} ya estaba COMPLETED: no hay nada que reejecutar",
                        identificadorCorrida, nombreJob);
                codigoSalida = 0;
                return;
            } catch (JobExecutionAlreadyRunningException ex) {
                log.error("[REEJECUCION] Ya hay una ejecucion de {} en curso para la corrida {}",
                        nombreJob, identificadorCorrida);
                codigoSalida = 3;
                return;
            } catch (Exception ex) {
                log.error("[REEJECUCION] No se pudo ejecutar {} en el intento {}: {}", nombreJob, intento, ex.toString());
            }

            if (intento < maxIntentos) {
                log.info("[REEJECUCION] Se espera {} ms antes de reintentar. Spring Batch retoma desde lo que quedo pendiente.",
                        esperaMs);
                Thread.sleep(esperaMs);
            }
        }

        log.error("[REEJECUCION] {} no termino bien despues de {} intentos. Se requiere revision manual.",
                nombreJob, maxIntentos);
        codigoSalida = 1;
    }

    @Override
    public int getExitCode() {
        return codigoSalida;
    }
}
