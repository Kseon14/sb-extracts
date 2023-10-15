package com.am.sbextracts.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

@Configuration
public class EventConfig {

    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster applicationEventMulticaster(
            @Value("${THREAD_COUNT}") Integer threadsCount) {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
        ConcurrentTaskExecutor concurrentTaskExecutor = new ConcurrentTaskExecutor();
//        concurrentTaskExecutor.setConcurrentExecutor(Executors.newFixedThreadPool(threadsCount));
        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
        taskExecutor.setConcurrencyLimit(threadsCount);
        eventMulticaster.setTaskExecutor(taskExecutor);
        return eventMulticaster;
    }
}