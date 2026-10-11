package cl.duocuc.bancoxyz.batch.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;


@Configuration
public class BatchThreadingConfig {

    @Value("${app.batch.thread-pool-size:3}")
    private int poolSize;

    @Value("${app.batch.thread-queue-capacity:10}")
    private int queueCapacity;

    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("Batch-Thread-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
