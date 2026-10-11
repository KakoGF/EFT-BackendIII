package cl.duocuc.bancoxyz.batch.common.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;

public class JobLoggingListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobLoggingListener.class);

    private final String jobLabel;

    public JobLoggingListener(String jobLabel) {
        this.jobLabel = jobLabel;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("==================================================================");
        log.info(">>> INICIANDO JOB [{}] - ejecucion {} - parametros {}",
                jobLabel, jobExecution.getId(), jobExecution.getJobParameters());
        log.info("==================================================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("------------------------------------------------------------------");
        log.info(">>> JOB [{}] finalizado con estado: {}", jobLabel, jobExecution.getStatus());
        for (StepExecution step : jobExecution.getStepExecutions()) {
            log.info("    Step [{}] {} -> leidos: {} | escritos: {} | omitidos(skip): {} | commits: {} | rollbacks: {}",
                    step.getStepName(),
                    step.getStatus(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    step.getSkipCount(),
                    step.getCommitCount(),
                    step.getRollbackCount());
        }
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info(">>> RESULTADO: datos procesados y persistidos correctamente.");
        } else {
            log.warn(">>> RESULTADO: el job termino con estado {}. Revisar las excepciones.", jobExecution.getStatus());
        }
        log.info("==================================================================");
    }
}
