package racetimingms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class TaskSchedulerConfig {

    @Value("${app.task-scheduler-enabled}")
    private boolean isTaskSchedulerEnabled;

    @Bean
    @Primary
    public TaskScheduler taskScheduler() {
        if (!isTaskSchedulerEnabled) {
            return new ThreadPoolTaskScheduler();
        }

        int cpuCores = Runtime.getRuntime().availableProcessors();
        int poolSize = cpuCores + 1;

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("MyScheduledTask-");
        scheduler.initialize();
        return scheduler;
    }
}
