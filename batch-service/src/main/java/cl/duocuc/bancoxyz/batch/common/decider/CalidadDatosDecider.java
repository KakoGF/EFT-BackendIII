package cl.duocuc.bancoxyz.batch.common.decider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;

public class CalidadDatosDecider implements JobExecutionDecider {

    private static final Logger log = LoggerFactory.getLogger(CalidadDatosDecider.class);

    private final String nombreStep;
    private final double umbralAlertaProporcion;

    public CalidadDatosDecider(String nombreStep, double umbralAlertaProporcion) {
        this.nombreStep = nombreStep;
        this.umbralAlertaProporcion = umbralAlertaProporcion;
    }

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        StepExecution procesamiento = jobExecution.getStepExecutions().stream()
                .filter(se -> se.getStepName().equals(nombreStep))
                .findFirst()
                .orElse(null);

        if (procesamiento == null || procesamiento.getReadCount() == 0) {
            return new FlowExecutionStatus("SIN_INCIDENCIAS");
        }

        long omitidos = procesamiento.getSkipCount();
        long leidos = procesamiento.getReadCount() + procesamiento.getReadSkipCount();
        double proporcion = (double) omitidos / leidos;
        log.info("[DECIDER] {}: {} omitidos de {} leidos ({}%), umbral {}%",
                nombreStep, omitidos, leidos, String.format("%.1f", proporcion * 100),
                String.format("%.0f", umbralAlertaProporcion * 100));

        if (omitidos == 0) {
            return new FlowExecutionStatus("SIN_INCIDENCIAS");
        }
        if (proporcion >= umbralAlertaProporcion) {
            return new FlowExecutionStatus("CALIDAD_CRITICA");
        }
        return new FlowExecutionStatus("CON_ALERTAS");
    }
}
