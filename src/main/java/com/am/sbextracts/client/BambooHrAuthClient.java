package com.am.sbextracts.client;

import com.am.sbextracts.config.FeignClientFormPostConfig;
import lombok.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(value = "bambooHrAuth", configuration = FeignClientFormPostConfig.class,
        url = "https://${COMPANY_NAME}.bamboohr.com")
public interface BambooHrAuthClient {

    @GetMapping(value = "auth/check_session?isOnboarding=false",
            consumes = "application/json")
    SessionInfo getSessionInfo(@RequestHeader Map<String, String> headerMap);

    @Value
    class SessionInfo {
        int SessionMinutesLeft;
        String CSRFToken;
        String error;
    }
}
