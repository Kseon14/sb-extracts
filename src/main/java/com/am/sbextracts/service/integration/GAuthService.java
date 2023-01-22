package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.GoogleAuthClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.SlackSlashCommandRequest;
import com.am.sbextracts.service.ResponderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.gmail.GmailScopes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
@Service
public class GAuthService {

    private static final TypeReference<HashMap<String, GoogleClientSecrets.Details>> TYPE_REF
            = new TypeReference<>() {
    };
    private final GoogleAuthClient googleAuthClient;
    public static final String APPLICATION_NAME = "BCH-Upload";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String PATTERN = "%s/%s-%s";
    private final ObjectMapper objectMapper;

    @Value("${google.authJsons}")
    private final String authJsons;
    @Value("${app.url}")
    private final String url;

    public static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public static final Map<String, String> CODE_VERIFIER = new ConcurrentHashMap<>();

    @Getter
    CompletableFuture<GoogleTokenResponse> tokenFuture;

    private final ResponderService slackResponderService;

    public void reAuth(SlackSlashCommandRequest slackSlashCommandRequest) {
        String initiatorSlackId = slackSlashCommandRequest.getUser_id();
        final String fileName = String.format(PATTERN, TOKENS_DIRECTORY_PATH,
                initiatorSlackId, GoogleCredential.class.getSimpleName());

        log.info("Token removed: {}", new java.io.File(fileName).delete());
        slackResponderService.log(initiatorSlackId, "Token Removed");
        slackResponderService.log(initiatorSlackId, "New Token creation initiated...");
        getCredentials(initiatorSlackId, StringUtils.equals(slackSlashCommandRequest.getText(), "with email"));
        log.info("New Token created");
        slackResponderService.log(initiatorSlackId, "New Token created");
    }

    private String getCodeChallenge(final String initiatorSlackId) {
        log.debug("Generate code challenge...");
        CODE_VERIFIER.put(initiatorSlackId, RandomStringUtils
                .random(43, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"));
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256")
                            .digest(CODE_VERIFIER.get(initiatorSlackId).getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public GoogleCredential deserialize(String initiatorSlackId) {
        log.debug("Starting deserialize credentials...");
        String fileName = String.format(PATTERN, TOKENS_DIRECTORY_PATH,
                initiatorSlackId, GoogleCredential.class.getSimpleName());
        if (new java.io.File(fileName).exists()) {
            log.debug("Credentials file exists ...");
            try (FileInputStream fin = new FileInputStream(fileName);
                 ObjectInputStream ois = new ObjectInputStream(fin)) {
                StoredCredential storedCredential = (StoredCredential) ois.readObject();
                return new GoogleCredential.Builder()
                        .setTransport(getNetHttpTransport())
                        .setJsonFactory(JSON_FACTORY)
                        .setClientSecrets(new GoogleClientSecrets().setInstalled(getCredFromLocalSource(initiatorSlackId)))
                        .build()
                        .setAccessToken(storedCredential.getAccessToken())
                        .setRefreshToken(storedCredential.getRefreshToken())
                        .setExpirationTimeMilliseconds(storedCredential.getExpirationTimeMilliseconds());
            } catch (Exception ex) {
                log.error("Error during deserialize credential", ex);
                return null;
            }
        } else {
            log.debug("Credentials file not exists ...");
            return null;
        }
    }

    public GoogleClientSecrets.Details getCredFromLocalSource(String initiatorSlackId) {
        log.debug("Getting credentials from local source");
        GoogleClientSecrets.Details details;
        try {
            details = objectMapper.readValue(authJsons, TYPE_REF)
                    .get(initiatorSlackId);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        details.setAuthUri("https://accounts.google.com/o/oauth2/auth");
        details.setTokenUri("https://oauth2.googleapis.com/token");
        details.setRedirectUris(Collections.singletonList(
                String.format("%s/%s", url, initiatorSlackId)));
        return details;
    }

    @SbExceptionHandler
    public static void serialize(GoogleCredential googleCredential, String initiatorSlackId) {
        log.debug("Starting serialization...");
        try {
            Files.createDirectories(Paths.get(TOKENS_DIRECTORY_PATH));
            try (FileOutputStream fos = new FileOutputStream(String.format(PATTERN, TOKENS_DIRECTORY_PATH,
                    initiatorSlackId, GoogleCredential.class.getSimpleName()));
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(new StoredCredential(googleCredential));
            }
        } catch (IOException ex) {
            throw new SbExtractsException("Error during serilization", ex, initiatorSlackId);
        }
    }


    void reAuth(String initiatorSlackId) {
        log.info("reauth for google");
        GoogleCredential googleCredential = deserialize(initiatorSlackId);
        if (googleCredential == null) {
            throw new IllegalStateException("cred file not exist");
        }
        objectMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
        GoogleClientSecrets.Details details =
                getCredFromLocalSource(initiatorSlackId);
        if (details == null) {
            throw new IllegalArgumentException("google account not found for " + initiatorSlackId);
        }
        String tokenResponse = googleAuthClient.getToken(
                Map.of("refresh_token", googleCredential.getRefreshToken(),
                        "client_id", details.getClientId(),
                        "client_secret", details.getClientSecret(),
                        "grant_type", "refresh_token")
        );
        GoogleTokenResponse googleTokenResponse;
        try {
            googleTokenResponse = objectMapper.readValue(tokenResponse, GoogleTokenResponse.class);
        } catch (JsonProcessingException e) {
            throw new SbExtractsException("Values could not be parsed", e, initiatorSlackId);
        }
        googleCredential.setAccessToken(googleTokenResponse.getAccessToken());
        serialize(googleCredential, initiatorSlackId);
    }


    static void removeToken(String initiatorSlackId) {
        String fileName = String.format(PATTERN, TOKENS_DIRECTORY_PATH,
                initiatorSlackId, GoogleCredential.class.getSimpleName());
        log.info("removing invalid credentials");
        try {
            Files.delete(Path.of(fileName));
        } catch (IOException e) {
            throw new SbExtractsException("Token could not be deleted", e, initiatorSlackId);
        }
    }

    public synchronized Credential getCredentials(String initiatorSlackId, boolean withMail) {

        final GoogleCredential googleCredential = deserialize(initiatorSlackId);
        if (googleCredential != null) {
            return googleCredential;
        }

        log.debug("Getting credentials from google...");
        GoogleClientSecrets.Details details =
                getCredFromLocalSource(initiatorSlackId);
        String redirectUrl = String.format("https://accounts.google.com/o/oauth2/v2/auth?" +
                        "access_type=offline" +
                        "&client_id=%s" +
                        "&redirect_uri=%s" +
                        "&response_type=code" +
                        "&code_challenge=%s" +
                        "&code_challenge_method=S256" +
                        "&scope=%s%s" +
                        "&prompt=consent",
                details.getClientId(),
                getRedirectURI(initiatorSlackId),
                getCodeChallenge(initiatorSlackId),
                DriveScopes.DRIVE,
                withMail ? " " + GmailScopes.GMAIL_SEND : "");

        log.info(redirectUrl);
        slackResponderService.log(initiatorSlackId,
                String.format("<%s|Please approve access to Google Drive %s>",
                        redirectUrl, withMail ? "and Google Mail" : ""));

        tokenFuture = new CompletableFuture<>();

        try {
            return tokenFuture.thenApply(token -> {
                log.debug("Credentials from google. received..");
                GoogleCredential googleCred = new GoogleCredential.Builder()
                        .setTransport(getNetHttpTransport())
                        .setJsonFactory(JSON_FACTORY)
                        .setClientSecrets(new GoogleClientSecrets().setInstalled(details))
                        .build()
                        .setFromTokenResponse(token);

                serialize(googleCred, initiatorSlackId);
                return googleCred;
            }).get(50, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            slackResponderService.log(initiatorSlackId, "Some problem with credential retrieving..");
            throw new IllegalStateException(e);
        }
    }


    public static NetHttpTransport getNetHttpTransport() {
        try {
            return GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getRedirectURI(String slackId) {
        return String.format("%s/api/gauth/%s", url, slackId);
    }
}
