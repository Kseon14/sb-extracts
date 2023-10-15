package com.am.sbextracts.client;

import com.am.sbextracts.config.FeignClientFormPostConfig;
import lombok.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(value = "nsInvoiceDownload", configuration = FeignClientFormPostConfig.class,
        url = "${NS_URL}")
public interface NetSuiteFileClient {

    @GetMapping(
            value = "app/common/media/mediaitemfolders.nl?sortcol=sortname&sortdir=ASC&segment=1&frame=b")
    feign.Response getInvoices(@RequestHeader Map<String, String> headerMap, @SpringQueryMap FolderParams folderParams);

    @GetMapping(value = "core/media/media.nl",
            consumes = MediaType.APPLICATION_PDF_VALUE)
    byte[] getPdf(@RequestHeader Map<String, String> headerMap, @SpringQueryMap Map<String, String> request);

    @GetMapping(value = "session-status.json?_={timeStamp}",
            consumes = "application/json")
    SessionInfo getSessionInfo(@RequestHeader Map<String, String> headerMap, @PathVariable long timeStamp);

    @Value(staticConstructor = "of")
    class FolderParams {
        int folder;
    }

    @Value
    class SessionInfo {
        String at;
        String ct;
        String it;
    }
}
