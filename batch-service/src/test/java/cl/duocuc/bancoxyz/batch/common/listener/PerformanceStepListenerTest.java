package cl.duocuc.bancoxyz.batch.common.listener;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceStepListenerTest {

    @Test
    void afterStepDevuelveElExitStatusQueYaTraeElStep() {
        PerformanceStepListener listener = new PerformanceStepListener();
        StepExecution stepExecution = nuevaStepExecution("stepDePrueba", 10);
        stepExecution.setExitStatus(ExitStatus.COMPLETED);

        listener.beforeStep(stepExecution);
        ExitStatus resultado = listener.afterStep(stepExecution);
        assertThat(resultado).isEqualTo(ExitStatus.COMPLETED);
    }

    @Test
    void noRompeSiElStepNoLeyoNingunRegistro() {
        PerformanceStepListener listener = new PerformanceStepListener();
        StepExecution stepExecution = nuevaStepExecution("stepVacio", 0);
        stepExecution.setExitStatus(ExitStatus.COMPLETED);

        listener.beforeStep(stepExecution);
        ExitStatus resultado = listener.afterStep(stepExecution);
        assertThat(resultado).isEqualTo(ExitStatus.COMPLETED);
    }

    private StepExecution nuevaStepExecution(String nombreStep, int registrosLeidos) {
        JobExecution jobExecution = new JobExecution(1L,
                new org.springframework.batch.core.JobParameters());
        jobExecution.setJobInstance(new JobInstance(1L, "jobDePrueba"));
        StepExecution stepExecution = new StepExecution(nombreStep, jobExecution);
        stepExecution.setStatus(BatchStatus.STARTED);
        for (int i = 0; i < registrosLeidos; i++) {
            stepExecution.setReadCount(stepExecution.getReadCount() + 1);
        }
        return stepExecution;
    }
}
