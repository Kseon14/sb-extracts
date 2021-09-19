package com.am.sbextracts.client;

import com.am.sbextracts.FeignClientFormPostConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@FeignClient(value = "googleAuth", configuration = FeignClientFormPostConfig.class, url = "https://oauth2.googleapis.com/token")
public interface GoogleAuthClient {

    @RequestMapping(method = RequestMethod.POST,
            consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    String getToken(@RequestBody Map<String, ?> request);


}