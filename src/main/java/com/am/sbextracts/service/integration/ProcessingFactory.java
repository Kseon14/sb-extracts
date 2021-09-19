package com.am.sbextracts.service.integration;

import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.am.sbextracts.vo.View;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingFactory {

    private final ProcessSignedService processSignedService;
    private final ProcessDebtorsService processDebtorsService;
    private final ProcessMarkupService processMarkupService;
    private final ProcessingInvoiceService processingInvoiceService;
    private final ProcessDebtorsPushService processDebtorsPushService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private Map<View.ModalActionType, Process> processorsMap;

    @PostConstruct
    public void initMap(){
        processorsMap =
                Map.of(View.ModalActionType.SIGNED, processSignedService,
                        View.ModalActionType.DEBTORS, processDebtorsService,
                        View.ModalActionType.MARKUP, processMarkupService,
                        View.ModalActionType.INVOICE_DOWNLOAD, processingInvoiceService,
                        View.ModalActionType.PUSH_DEBTORS, processDebtorsPushService);
    }

    public void startProcessing(SlackInteractiveEvent slackInteractiveEvent){
        View.ModalActionType type = slackInteractiveEvent.getView().getType();
        Process process = processorsMap.get(type);
        log.info("Start execution with {}", type);

        if (process != null) {
            executorService.execute(() ->
                    process.process(InternalSlackEventResponse.convert(slackInteractiveEvent)));
        }
    }

}
