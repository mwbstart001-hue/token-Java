package com.example.tokenservice.dispatcher;

import com.example.tokenservice.config.TokenProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class DispatcherConfig {

    private final TokenProperties tokenProperties;

    public DispatcherConfig(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
    }

    @Bean(name = "tokenTaskExecutor")
    public Executor tokenTaskExecutor() {
        TokenProperties.Scheduler config = tokenProperties.getScheduler();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix("token-dispatcher-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        return executor;
    }
}
