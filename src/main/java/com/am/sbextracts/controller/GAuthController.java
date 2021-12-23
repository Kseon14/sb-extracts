package com.am.sbextracts.controller;

import com.am.sbextracts.client.GoogleAuthClient;
import com.am.sbextracts.service.integration.GDriveService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
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
    private final GDriveService gDriveService;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @GetMapping("api/gauth/{slackId}")
    @ResponseBody
    public ResponseEntity getAuthCode(@RequestParam(name = "code") String code,
                                      @PathVariable(name = "slackId") String initiatorSlackId) {
        objectMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
        GoogleClientSecrets.Details details = gDriveService.getCredFromLocalSource(initiatorSlackId);
        String tokenResponse = googleAuthClient.getToken(
                Map.of("code", code,
                        "client_id", details.getClientId(),
                        "client_secret", details.getClientSecret(),
                        "grant_type", "authorization_code",
                        "redirect_uri", gDriveService.getRedirectURI(initiatorSlackId))
        );
        GoogleTokenResponse googleTokenResponse = objectMapper.readValue(tokenResponse, GoogleTokenResponse.class);
        gDriveService.setToken(googleTokenResponse);

        return new ResponseEntity("Now you can close this page page", HttpStatus.OK);

    }

}
