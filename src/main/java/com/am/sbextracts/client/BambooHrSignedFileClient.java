package com.am.sbextracts.client;

import com.am.sbextracts.FeignClientFormPostConfig;
import lombok.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(value = "bambooHrSignedFile", configuration = FeignClientFormPostConfig.class,
        url = "https://${COMPANY_NAME}.bamboohr.com")
public interface BambooHrSignedFileClient {

    @GetMapping(value = "reports/?view=signedDocuments")
    feign.Response getSignedDocumentList(@RequestHeader Map<String, String> headerMap);

    @GetMapping(value = "reports/esignatures/{id}")
    feign.Response getSignatureReport(@RequestHeader Map<String, String> headerMap, @PathVariable String id);

    @GetMapping(value = "ajax/fetcher.php?doc={docId}&format=pdf&employee=true",
            consumes = MediaType.APPLICATION_PDF_VALUE)
    byte[] getPdf(@RequestHeader Map<String, String> headerMap, @PathVariable String docId);

    @Value
    class SessionInfo {
        int SessionMinutesLeft;
        String CSRFToken;
    }
}
