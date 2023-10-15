package com.am.sbextracts.client;

import com.am.sbextracts.config.FeignClientFormPostConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@FeignClient(value = "googleAuth", configuration = FeignClientFormPostConfig.class,
        url = "https://oauth2.googleapis.com/token")
public interface GoogleAuthClient {

    @PostMapping(consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    String getToken(@RequestBody Map<String, ?> request);


}