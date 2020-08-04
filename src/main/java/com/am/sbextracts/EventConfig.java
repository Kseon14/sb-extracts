package com.am.sbextracts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

import java.util.concurrent.Executors;

@Configuration
public class EventConfig {

        @Bean(name = "applicationEventMulticaster")
        public ApplicationEventMulticaster applicationEventMulticaster() {
            SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
            ConcurrentTaskExecutor concurrentTaskExecutor = new ConcurrentTaskExecutor();
            concurrentTaskExecutor.setConcurrentExecutor(Executors.newFixedThreadPool(5));
            eventMulticaster.setTaskExecutor(concurrentTaskExecutor);
            return eventMulticaster;
    }
}
