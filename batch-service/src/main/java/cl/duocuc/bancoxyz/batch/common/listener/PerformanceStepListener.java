package cl.duocuc.bancoxyz.batch.common.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

public class PerformanceStepListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(PerformanceStepListener.class);
    private static final long MB = 1024L * 1024L;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        stepExecution.getExecutionContext().putLong("perf.inicio", System.currentTimeMillis());
        log.info("[PERF] Step [{}] inicia | memoria JVM en uso: {} MB de {} MB max.",
                stepExecution.getStepName(), memoriaUsadaMb(), Runtime.getRuntime().maxMemory() / MB);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long inicio = stepExecution.getExecutionContext().getLong("perf.inicio", System.currentTimeMillis());
        long duracionMs = Math.max(1, System.currentTimeMillis() - inicio);
        long leidos = stepExecution.getReadCount();
        double throughput = leidos * 1000.0 / duracionMs;

        log.info("[PERF] Step [{}] finalizo en {} ms | leidos: {} | throughput: {} reg/seg | memoria JVM: {} MB",
                stepExecution.getStepName(), duracionMs, leidos, String.format("%.1f", throughput), memoriaUsadaMb());
        return stepExecution.getExitStatus();
    }

    private long memoriaUsadaMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / MB;
    }
}
