package com.am.sbextracts.service.integration;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.LockIndicator;
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
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GDriveService {

    @Value("${google.authJson}")
    private final String authJson;

    @Value("${app.url}")
    private final String url;

    private GoogleTokenResponse token;

    private final ObjectMapper objectMapper;
    private final ResponderService slackResponderService;

    private final LockIndicator lock = new LockIndicator();

    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String APPLICATION_NAME = "BCH-Upload";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @SneakyThrows
    public void uploadFile(File fileMetadata, byte[] fileBody, String type, String initiatorSlackId) {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, initiatorSlackId))
                .setApplicationName(APPLICATION_NAME).build();

        ByteArrayContent mediaContent = new ByteArrayContent(type, fileBody);
        File file = service.files().create(fileMetadata, mediaContent).setFields("id").execute();
        log.info("File ID: {}", file.getId());
    }

    public void setToken(GoogleTokenResponse token) {
        synchronized (lock) {
            this.token = token;
            lock.setLocked(false);
            lock.notify();
        }
    }

    @SneakyThrows
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, String initiatorSlackId) {
        GoogleCredential googleCredential = deserialize();
        if (googleCredential != null) {
            return googleCredential;
        }

        GoogleClientSecrets.Details details = objectMapper.readValue(authJson, GoogleClientSecrets.Details.class);
        String redirectUrl = String.format("https://accounts.google.com/o/oauth2/auth?" +
                "access_type=offline" +
                "&client_id=%s" +
                "&redirect_uri=%s" +
                "&response_type=code" +
                "&scope=%s", details.getClientId(), getRedirectURI(), DriveScopes.DRIVE);

        slackResponderService.log(initiatorSlackId, redirectUrl);

        waitToken();

        googleCredential = new GoogleCredential.Builder()
                .setTransport(HTTP_TRANSPORT)
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(new GoogleClientSecrets().setInstalled(details))
                .build()
                .setFromTokenResponse(token);

        serialize(googleCredential);
        return googleCredential;
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

    public String getRedirectURI() {
        return url + "/api/gauth";
    }

    @SneakyThrows
    @SbExceptionHandler
    public boolean isFolderExist(String fileId, String initiatorSlackId) {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Optional<File> folder;
        try {
            Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, initiatorSlackId))
                    .setApplicationName(APPLICATION_NAME).build();
            folder = Optional.of(service
                    .files()
                    .get(fileId)
                    .execute());
        } catch (GoogleJsonResponseException ex) {
            throw new SbExtractsException("Error during folder validation", ex, initiatorSlackId);
        }
        return folder.map(f -> f.getMimeType().equals("application/vnd.google-apps.folder"))
                .orElseThrow(IllegalStateException::new);
    }

    @SneakyThrows
    public java.io.File getFile(String fileName, String initiatorSlackId) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, initiatorSlackId))
                .setApplicationName(APPLICATION_NAME).build();
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

    public void saveFile(java.io.File file, String logFileName, InternalSlackEventResponse slackEventResponse) {
        saveFile(file, logFileName, slackEventResponse.getInitiatorUserId(), slackEventResponse.getGFolderId());
    }

    @SneakyThrows
    @SbExceptionHandler
    public void saveFile(java.io.File file, String logFileName, String initiatorUserId, String gFolderId) {
        try {
            if (file != null) {
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

    @SneakyThrows
    public void updateFile(String fileId, File file, FileContent fileContent, String initiatorSlackId) {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, initiatorSlackId))
                .setApplicationName(APPLICATION_NAME).build();
        log.info("{} will be uploaded", fileId);
        File updated = service.files().update(fileId, file, fileContent).execute();
        log.info("{} uploaded", updated.getId());
    }


    public static void serialize(GoogleCredential googleCredential) throws IOException {
        Files.createDirectories(Paths.get(TOKENS_DIRECTORY_PATH));
        FileOutputStream fos = new FileOutputStream(TOKENS_DIRECTORY_PATH + "/" + GoogleCredential.class.getSimpleName());
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(new StoredCredential(googleCredential));
        oos.close();
    }

    public static GoogleCredential deserialize() {
        String fileName = TOKENS_DIRECTORY_PATH + "/" + GoogleCredential.class.getSimpleName();
        if (new java.io.File(fileName).exists()) {
            try (FileInputStream fin = new FileInputStream(fileName);
                 ObjectInputStream ois = new ObjectInputStream(fin)) {
                StoredCredential storedCredential = (StoredCredential) ois.readObject();
                return new GoogleCredential()
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
