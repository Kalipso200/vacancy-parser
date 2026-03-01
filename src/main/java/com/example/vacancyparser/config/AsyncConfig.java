package com.example.vacancyparser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

@Configuration
public class AsyncConfig {

    @Bean(name = "parserExecutor")
    public Executor parserExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("parser-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean(name = "forkJoinPool")
    public ForkJoinPool forkJoinPool() {
        return ForkJoinPool.commonPool();
    }

    @Bean(name = "scheduledExecutor")
    public ExecutorService scheduledExecutor() {
        return Executors.newScheduledThreadPool(2);
    }
}