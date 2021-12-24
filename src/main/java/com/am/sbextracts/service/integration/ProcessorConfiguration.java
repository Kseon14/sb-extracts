package com.am.sbextracts.service.integration;

import com.am.sbextracts.vo.View;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class ProcessorConfiguration {

    private final ProcessSignedService processSignedService;
    private final ProcessDebtorsService processDebtorsService;
    private final ProcessMarkupService processMarkupService;
    private final ProcessingInvoiceService processingInvoiceService;
    private final ProcessDebtorsPushService processDebtorsPushService;

    @Bean
    public Map<View.ModalActionType, Process> processorsMap() {
        return Map.of(View.ModalActionType.SIGNED, processSignedService,
                View.ModalActionType.DEBTORS, processDebtorsService,
                View.ModalActionType.MARKUP, processMarkupService,
                View.ModalActionType.INVOICE_DOWNLOAD, processingInvoiceService,
                View.ModalActionType.PUSH_DEBTORS, processDebtorsPushService);
    }
}
