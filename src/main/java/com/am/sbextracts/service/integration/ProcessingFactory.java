package com.am.sbextracts.service.integration;

import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.am.sbextracts.vo.View;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingFactory {

    private final ProcessorConfiguration processors;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public void startProcessing(SlackInteractiveEvent slackInteractiveEvent){
        View.ModalActionType type = slackInteractiveEvent.getView().getType();
        Process process = processors.processorsMap().get(type);
        log.info("Start execution with {}", type);

        if (process != null) {
            executorService.execute(() ->
                    process.process(InternalSlackEventResponse.convert(slackInteractiveEvent)));
        }
    }

}
