package com.am.sbextracts.client;

import com.am.sbextracts.FeignClientFormPostConfig;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;

@FeignClient(value = "slackApi", configuration = FeignClientFormPostConfig.class)
public interface SlackApiClient {

    @GetMapping
    Response getFile(URI host);

}