package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.GoogleAuthClient;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.LockIndicator;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
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
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class GAuthService {

    private static final TypeReference<HashMap<String, GoogleClientSecrets.Details>> TYPE_REF = new TypeReference<>() {
    };
    private GoogleTokenResponse token;
    private final GoogleAuthClient googleAuthClient;
    private final LockIndicator lock = new LockIndicator();
    public static final String APPLICATION_NAME = "BCH-Upload";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String PATTERN = "%s/%s-%s";
    private final ObjectMapper objectMapper;

    @Value("${google.authJsons}")
    private final String authJsons;
    @Value("${app.url}")
    private final String url;

    public static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Getter
    private static final Map<String, String> codeVerifier = new HashMap<>();

    private final ResponderService slackResponderService;


    public void setToken(GoogleTokenResponse token) {
        synchronized (lock) {
            this.token = token;
            lock.setLocked(false);
            lock.notify();
        }
    }

    @SneakyThrows
    private void waitToken() {
        synchronized (lock) {
            lock.setLocked(true);
            while (lock.isLocked()) {
                lock.wait();
            }
        }
    }

    @SneakyThrows
    public String getCodeChallenge(final String initiatorSlackId) {
        codeVerifier.put(initiatorSlackId, RandomStringUtils
                .random(43, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(codeVerifier.get(initiatorSlackId).getBytes(StandardCharsets.US_ASCII)));
    }

    public GoogleCredential deserialize(String initiatorSlackId) {
        String fileName = String.format(PATTERN, TOKENS_DIRECTORY_PATH,
                initiatorSlackId, GoogleCredential.class.getSimpleName());
        if (new java.io.File(fileName).exists()) {
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
            return null;
        }
    }

    @SneakyThrows
    public GoogleClientSecrets.Details getCredFromLocalSource(String initiatorSlackId) {
        GoogleClientSecrets.Details details = objectMapper.readValue(authJsons, TYPE_REF)
                .get(initiatorSlackId);
        details.setAuthUri("https://accounts.google.com/o/oauth2/auth");
        details.setTokenUri("https://oauth2.googleapis.com/token");
        details.setRedirectUris(Collections.singletonList(
                String.format("%s/%s", url, initiatorSlackId)));
        return details;
    }

    public static void serialize(GoogleCredential googleCredential, String initiatorSlackId) throws IOException {
        Files.createDirectories(Paths.get(TOKENS_DIRECTORY_PATH));
        try (FileOutputStream fos = new FileOutputStream(String.format(PATTERN, TOKENS_DIRECTORY_PATH,
                initiatorSlackId, GoogleCredential.class.getSimpleName()));
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(new StoredCredential(googleCredential));
        }
    }

    @SneakyThrows
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
        GoogleTokenResponse googleTokenResponse = objectMapper.readValue(tokenResponse, GoogleTokenResponse.class);
        googleCredential.setAccessToken(googleTokenResponse.getAccessToken());
        serialize(googleCredential, initiatorSlackId);
    }

    @SneakyThrows
    static void removeToken(String initiatorSlackId) {
        String fileName = String.format(PATTERN, TOKENS_DIRECTORY_PATH,
                initiatorSlackId, GoogleCredential.class.getSimpleName());
        log.info("removing invalid credentials");
        Files.delete(Path.of(fileName));
    }

    @SneakyThrows
    public Credential getCredentials(String initiatorSlackId, boolean withMail) {
        GoogleCredential googleCredential = deserialize(initiatorSlackId);
        if (googleCredential != null) {
            return googleCredential;
        }

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

        slackResponderService.log(initiatorSlackId,
                String.format("<%s|Please approve access to Google Drive>", redirectUrl));

        waitToken();

        googleCredential = new GoogleCredential.Builder()
                .setTransport(getNetHttpTransport())
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(new GoogleClientSecrets().setInstalled(details))
                .build()
                .setFromTokenResponse(token);

        serialize(googleCredential, initiatorSlackId);
        return googleCredential;
    }

    @SneakyThrows
    public static NetHttpTransport getNetHttpTransport() {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    public String getRedirectURI(String slackId) {
        return url + "/api/gauth/" + slackId;
    }
}
