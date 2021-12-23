package com.am.sbextracts.client;

import com.am.sbextracts.FeignClientFormPostConfig;
import lombok.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

@FeignClient(value = "bambooHrAuth", configuration = FeignClientFormPostConfig.class, url = "https://${app.company.name}.bamboohr.com")
public interface BambooHrAuthClient {

    @RequestMapping(method = RequestMethod.GET, value = "auth/check_session?isOnboarding=false", consumes = "application/json")
    SessionInfo getSessionInfo(@RequestHeader Map<String, String> headerMap);

    @Value
    class SessionInfo {
        int SessionMinutesLeft;
        String CSRFToken;
        String error;
    }
}
