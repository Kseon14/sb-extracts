package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrAuthClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class HeaderService {

    private final static String CSRF_HEADER = "X-CSRF-Token";
    private final static String COOKIE = "Cookie";
    private final static String AUTHORIZATION = "Authorization";

    private final BambooHrAuthClient bambooHrAuthClient;

    @Value("${bamboo.apiKey}")
    private final String apiKey;

    private String csrfToken;

    @SbExceptionHandler
    public Map<String, String> getBchHeaders(String sessionId, String initiatorSlackId) {
        String phpsessid = String.format("%s=%s", "PHPSESSID", sessionId);
        BambooHrAuthClient.SessionInfo sessionInfo;
        if (csrfToken == null) {
            try {
               sessionInfo = bambooHrAuthClient.getSessionInfo(Map.of(COOKIE, phpsessid));
            } catch (Exception ex) {
                throw new SbExtractsException("Please login into the Bamboo", ex, initiatorSlackId);
            }
            if(sessionInfo != null) {
                csrfToken = sessionInfo.getCSRFToken();
            }
        }
        return Map.of(COOKIE, phpsessid, CSRF_HEADER, csrfToken);
    }

    public static Map<String, String> getNsHeaders(String sessionId) {
        String jsessionid = String.format("%s=%s", "JSESSIONID", sessionId);
        return Map.of(COOKIE, jsessionid);
    }

    public Map<String, String> getHeaderForBchApi() {
        return Map.of(AUTHORIZATION, "Basic " + new String(Base64Utils.encode((apiKey + ":").getBytes())));
    }
}
