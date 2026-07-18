package com.gogoga.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置类
 *
 * @author JackWei
 * @since 2026-01-22
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("mtAsyncTaskExecutor")
    public Executor mtAsyncTaskExecutor(MtProperties mtProperties) {
        MtProperties.AsyncTask asyncTask = mtProperties.getAsyncTask();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncTask.getExecutorCorePoolSize());
        executor.setMaxPoolSize(asyncTask.getExecutorMaxPoolSize());
        executor.setQueueCapacity(asyncTask.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("mt-async-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Async executor initialized, name=mtAsyncTaskExecutor, corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), asyncTask.getExecutorQueueCapacity());
        return executor;
    }

    @Bean("mtLlmTaskExecutor")
    public ThreadPoolTaskExecutor mtLlmTaskExecutor(MtProperties mtProperties) {
        int concurrency = Math.max(mtProperties.getAsyncTask().getLlmExecutorConcurrency(), 1);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("mt-llm-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Async executor initialized, name=mtLlmTaskExecutor, concurrency={}, queueCapacity=0", concurrency);
        return executor;
    }
}
