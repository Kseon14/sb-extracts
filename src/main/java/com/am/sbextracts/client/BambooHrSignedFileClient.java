package com.am.sbextracts.client;

import com.am.sbextracts.FeignClientFormPostConfig;
import lombok.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

@FeignClient(value = "bambooHrSignedFile", configuration = FeignClientFormPostConfig.class, url = "https://squadukraine.bamboohr.com")
public interface BambooHrSignedFileClient {

    @RequestMapping(method = RequestMethod.GET, value = "reports/?view=signedDocuments")
    feign.Response getSignedDocumentList(@RequestHeader Map<String, String> headerMap);

    @RequestMapping(method = RequestMethod.GET, value = "reports/esignatures/?id={id}")
    feign.Response getSignatureReport(@RequestHeader Map<String, String> headerMap, @PathVariable String id);

    @RequestMapping(method = RequestMethod.GET, value = "ajax/fetcher.php?doc={docId}&format=pdf&employee=true", consumes = MediaType.APPLICATION_PDF_VALUE)
    byte[] getPdf(@RequestHeader Map<String, String> headerMap, @PathVariable String docId);

    @Value
    class SessionInfo {
        int SessionMinutesLeft;
        String CSRFToken;
    }
}
