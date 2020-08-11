package com.am.sbextracts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

import java.util.concurrent.Executors;

@Configuration
public class EventConfig {

    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster applicationEventMulticaster(
            @Value("${event.concurrent.threads}") Integer threadsCount) {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
        ConcurrentTaskExecutor concurrentTaskExecutor = new ConcurrentTaskExecutor();
        concurrentTaskExecutor.setConcurrentExecutor(Executors.newFixedThreadPool(threadsCount));
        eventMulticaster.setTaskExecutor(concurrentTaskExecutor);
        return eventMulticaster;
    }
}
