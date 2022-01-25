package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.GoogleAuthClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.InternalSlackEventResponse;
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
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class GDriveService {

    private static final TypeReference<HashMap<String, GoogleClientSecrets.Details>> TYPE_REF = new TypeReference<>() {
    };
    private static final int MAX_RETRY = 2;

    @Value("${google.authJsons}")
    private final String authJsons;

    @Value("${app.url}")
    private final String url;

    private GoogleTokenResponse token;

    private final ObjectMapper objectMapper;
    private final ResponderService slackResponderService;
    private final GoogleAuthClient googleAuthClient;

    private final LockIndicator lock = new LockIndicator();

    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String APPLICATION_NAME = "BCH-Upload";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Getter
    private static final Map<String, String> codeVerifier = new HashMap<>();

    @SneakyThrows
    public void uploadFile(File fileMetadata, byte[] fileBody, String type, String initiatorSlackId) {
        ByteArrayContent mediaContent = new ByteArrayContent(type, fileBody);
        File file;
        try {
            file = getService(initiatorSlackId).files().create(fileMetadata, mediaContent).setFields("id").execute();
            log.info("File ID: {}", file.getId());
        } catch (GoogleJsonResponseException ex) {
            if (ex.getStatusCode() == 401) {
                reAuth(initiatorSlackId);
                uploadFile(fileMetadata, fileBody, type, initiatorSlackId);
            }
            throw new Exception(ex);
        }
    }

    @SneakyThrows
    private void reAuth(String initiatorSlackId) {
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

    public void setToken(GoogleTokenResponse token) {
        synchronized (lock) {
            this.token = token;
            lock.setLocked(false);
            lock.notify();
        }
    }

    @SneakyThrows
    private Credential getCredentials(String initiatorSlackId) {
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
                "&scope=%s" +
                "&prompt=consent", details.getClientId(), getRedirectURI(initiatorSlackId), getCodeChallenge(initiatorSlackId), DriveScopes.DRIVE);

        slackResponderService.log(initiatorSlackId, redirectUrl);

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
    public String getCodeChallenge(final String initiatorSlackId) {
        codeVerifier.put(initiatorSlackId, RandomStringUtils
                .random(43, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(codeVerifier.get(initiatorSlackId).getBytes(StandardCharsets.US_ASCII)));
    }

    @SneakyThrows
    public GoogleClientSecrets.Details getCredFromLocalSource(String initiatorSlackId) {
        return objectMapper.readValue(authJsons, TYPE_REF).get(initiatorSlackId);
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

    public String getRedirectURI(String slackId) {
        return url + "/api/gauth/" + slackId;
    }

    @SneakyThrows
    @SbExceptionHandler
    public void validateFolderExistence(String fileId, String initiatorSlackId) {
        int attempt = 1;
        circuitBreaker(attempt, validateFolder(), fileId, initiatorSlackId);
    }

    private BiConsumer<String, String> validateFolder() {
        return (fileId, initiatorSlackId) -> {
            try {
                Optional.of(getService(initiatorSlackId)
                                .files()
                                .get(fileId)
                                .execute())
                        .map(f -> f.getMimeType().equals("application/vnd.google-apps.folder"))
                        .orElseThrow(() ->
                                new SbExtractsException("Error during folder validation", initiatorSlackId));
            } catch (IOException ex) {
                reAuth(initiatorSlackId);
            }
        };
    }

    private Drive getService(String initiatorSlackId) {
        return new Drive.Builder(getNetHttpTransport(), JSON_FACTORY, getCredentials(initiatorSlackId))
                .setApplicationName(APPLICATION_NAME).build();
    }

    @SneakyThrows
    private static NetHttpTransport getNetHttpTransport() {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @SneakyThrows
    private static int circuitBreaker(int attempt, BiConsumer<String, String> consumer, String fileId, String initiatorSlackId) {
        try {
            if (attempt <= MAX_RETRY) {
                consumer.accept(fileId, initiatorSlackId);
            }
            return attempt + 1;
        } catch (Exception ex) {
            removeToken(initiatorSlackId);
            return circuitBreaker(attempt + 1, consumer, fileId, initiatorSlackId);
        }
    }

    private static void removeToken(String initiatorSlackId) {
        String fileName = String.format("%s/%s-%s", TOKENS_DIRECTORY_PATH,
                initiatorSlackId, GoogleCredential.class.getSimpleName());
        log.info("removing invalid credentials");
        new java.io.File(fileName).delete();
    }

    @SneakyThrows
    public java.io.File getFile(String fileName, String initiatorSlackId) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Drive service = getService(initiatorSlackId);
        List<File> result = service
                .files()
                .list()
                .setSpaces("drive")
                .setPageSize(20)
                .setQ("name = '" + fileName + "' and trashed = false")
                .setFields("files(id, name)")
                .execute().getFiles();

        if (result.size() == 0) {
            return new java.io.File(fileName);
        }
        if (result.size() > 1) {
            throw new IllegalArgumentException("too many " + fileName + " files with the same name in gDrive");
        }

        String intName = result.get(0).getId();
        java.io.File file = new java.io.File(intName);
        try (OutputStream outputStream = new FileOutputStream(file)) {
            service.files().get(intName)
                    .executeMediaAndDownloadTo(byteArrayOutputStream);
            byteArrayOutputStream.writeTo(outputStream);
        }
        return file;
    }

    public void saveFile(java.io.File file, long dateOfModification, String logFileName, InternalSlackEventResponse slackEventResponse) {
        saveFile(file, dateOfModification, logFileName, slackEventResponse.getInitiatorUserId(), slackEventResponse.getGFolderId());
    }

    @SneakyThrows
    @SbExceptionHandler
    public void saveFile(java.io.File file, long dateOfModification, String logFileName, String initiatorUserId, String gFolderId) {
        if (dateOfModification >= 0 && file.lastModified() > 0 && dateOfModification < file.lastModified()) {
            try {
                if (file != null && file.exists()) {
                    com.google.api.services.drive.model.File logFile = new com.google.api.services.drive.model.File();
                    logFile.setName(logFileName);
                    logFile.setMimeType(MediaType.TEXT_PLAIN_VALUE);

                    if (StringUtils.equals(file.getName(), logFileName)) {
                        logFile.setParents(Collections.singletonList(gFolderId));
                        uploadFile(logFile, FileUtils.readFileToByteArray(file), MediaType.TEXT_PLAIN_VALUE, initiatorUserId);
                    } else {
                        updateFile(file.getName(), logFile, new FileContent(MediaType.TEXT_PLAIN_VALUE, file), initiatorUserId);
                    }

                    slackResponderService.log(initiatorUserId, "Google report updated");

                    log.info("Local report deleted: {}", file.delete());
                    log.info("Report updated: {}", file.getName());
                    log.info("DONE");
                    slackResponderService.log(initiatorUserId, "Done");
                }
            } catch (Exception ex) {
                throw new SbExtractsException("Error during upload to google-drive:", ex, initiatorUserId);
            }
        }
    }

    @SneakyThrows
    public void updateFile(String fileId, File file, FileContent fileContent, String initiatorSlackId) {
        log.info("{} will be uploaded", fileId);
        File updated = getService(initiatorSlackId).files().update(fileId, file, fileContent).execute();
        log.info("{} uploaded", updated.getId());
    }


    public static void serialize(GoogleCredential googleCredential, String initiatorSlackId) throws IOException {
        Files.createDirectories(Paths.get(TOKENS_DIRECTORY_PATH));
        FileOutputStream fos = new FileOutputStream(String.format("%s/%s-%s", TOKENS_DIRECTORY_PATH,
                initiatorSlackId, GoogleCredential.class.getSimpleName()));
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(new StoredCredential(googleCredential));
        oos.close();
    }

    public GoogleCredential deserialize(String initiatorSlackId) {
        String fileName = String.format("%s/%s-%s", TOKENS_DIRECTORY_PATH,
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

}
