package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrAuthClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeaderService {

    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String COOKIE = "Cookie";
    private static final String AUTHORIZATION = "Authorization";

    private final BambooHrAuthClient bambooHrAuthClient;

    @Value("${bamboo.apiKeys}")
    private final String apiKeys;
    private final ObjectMapper objectMapper;

    @SbExceptionHandler
    public Map<String, String> getBchHeaders(String sessionId, String initiatorSlackId) {
        String phpSessionId = String.format("%s=%s", "PHPSESSID", sessionId);
        BambooHrAuthClient.SessionInfo sessionInfo;
        try {
            sessionInfo = bambooHrAuthClient.getSessionInfo(Map.of(COOKIE, phpSessionId));
        } catch (Exception ex) {
            log.error("Error during sessionInfo retrieving", ex);
            throw new IllegalArgumentException("Please login into the Bamboo");
        }
        if (sessionInfo != null) {
            String csrfToken = sessionInfo.getCSRFToken();
            if (csrfToken == null) {
                throw new SbExtractsException(sessionInfo.getError(), initiatorSlackId);
            }
            return Map.of(COOKIE, phpSessionId, CSRF_HEADER, csrfToken);
        }
        throw new SbExtractsException("Error during csrf token retrieving", initiatorSlackId);
    }

    public static Map<String, String> getNsHeaders(String sessionId) {
        String jsessionid = String.format("%s=%s", "JSESSIONID", sessionId);
        return Map.of(COOKIE, jsessionid);
    }

    public Map<String, String> getHeaderForBchApi(String initiatorSlackId) {
        return Map.of(AUTHORIZATION, "Basic " + new String(Base64Utils.encode((getApiKey(initiatorSlackId) + ":").getBytes())));
    }

    @SbExceptionHandler
    private String getApiKey(String initiatorSlackId) {
        try {
            return objectMapper.readValue(apiKeys, HashMap.class).get(initiatorSlackId).toString();
        } catch (JsonProcessingException e) {
            throw new SbExtractsException("Error during parsing api key", initiatorSlackId);
        }
    }
}
