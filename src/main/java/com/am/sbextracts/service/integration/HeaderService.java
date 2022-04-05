package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrAuthClient;
import com.am.sbextracts.client.NetSuiteFileClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;

import java.time.Instant;
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
    private final NetSuiteFileClient netSuiteFileClient;

    @Value("${bamboo.apiKeys}")
    private final String apiKeys;
    private final ObjectMapper objectMapper;

    @SbExceptionHandler
    public Map<String, String> getBchHeaders(String sessionId, String initiatorSlackId) {
        log.debug("Getting bch headers...");
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

    @SbExceptionHandler
    public Map<String, String> getNsHeaders(String sessionId, String initiatorSlackId) {
        log.debug("Getting NS headers...");
        String jsessionid = String.format("%s=%s", "JSESSIONID", sessionId);
        Map<String, String> cookie = Map.of(COOKIE, jsessionid);
        Map<String, String> cookieForSessionRequest = new HashMap<>(cookie);
        cookieForSessionRequest.put("X-Supress-Cookie-JSessionID", "true");
        try {
            NetSuiteFileClient.SessionInfo sessionInfo = netSuiteFileClient.getSessionInfo(cookieForSessionRequest,
                    Instant.now().getEpochSecond());
            if (StringUtils.isBlank(sessionInfo.getCt())) {
                throw new SbExtractsException("Please login into NS", initiatorSlackId);
            }
        } catch (Exception ex) {
            log.error("Error during sessionInfo retrieving", ex);
            throw new IllegalArgumentException("Please login into the NS");
        }
        return cookie;
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
