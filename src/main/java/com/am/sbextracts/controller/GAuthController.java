package com.am.sbextracts.controller;

import com.am.sbextracts.client.GoogleAuthClient;
import com.am.sbextracts.service.integration.GAuthService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GAuthController {

    private final GoogleAuthClient googleAuthClient;
    @Lazy
    private final GAuthService gAuthService;
    private final ObjectMapper objectMapper;

    @GetMapping(value = "api/gauth/{slackId}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> getAuthCode(@RequestParam(name = "code") String code,
                                              @PathVariable(name = "slackId") String initiatorSlackId) {
        objectMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
        GoogleClientSecrets.Details details = gAuthService.getCredFromLocalSource(initiatorSlackId);
        String tokenResponse = googleAuthClient.getToken(
                Map.of("code", code,
                        "client_id", details.getClientId(),
                        "client_secret", details.getClientSecret(),
                        "code_verifier", GAuthService.CODE_VERIFIER.get(initiatorSlackId),
                        "grant_type", "authorization_code",
                        "redirect_uri", gAuthService.getRedirectURI(initiatorSlackId))
        );
        GoogleTokenResponse googleTokenResponse;
        try {
            googleTokenResponse = objectMapper.readValue(tokenResponse, GoogleTokenResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        gAuthService.getTokenFuture().complete(googleTokenResponse);

        return new ResponseEntity<>("Now you can close this page page", HttpStatus.OK);
    }

}
