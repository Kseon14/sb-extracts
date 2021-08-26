package com.am.sbextracts.service.integration;

import com.am.sbextracts.service.ResponderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GDriveService {

    @Value("${google.authJson}")
    private final String authJson;

    private final ObjectMapper objectMapper;
    private final ResponderService slackResponderService;

    private static final List<String> SCOPES = Arrays.asList(
            DriveScopes.DRIVE);
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

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, String initiatorSlackId) throws IOException {
        GoogleClientSecrets.Details details = objectMapper.readValue(authJson, GoogleClientSecrets.Details.class);
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthCodeWrapper(flow, receiver, slackResponderService,
                initiatorSlackId).authorize("user");
    }


    @SneakyThrows
    public java.io.File getFile(String fileName, String initiatorSlackId) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, initiatorSlackId))
                .setApplicationName(APPLICATION_NAME).build();
        List<File> result  = service
                    .files()
                    .list()
                .setSpaces("drive")
                .setPageSize(20)
                    .setQ("name = '"+fileName+"'")
                .setFields("files(id, name)")
                .execute().getFiles();

       if (result.size() == 0){
           return new java.io.File(fileName);
       }
       if (result.size() >1) {
           throw new IllegalArgumentException("too many "+fileName+" files with the same name in gDrive");
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

    @SneakyThrows
    public void updateFile(String fileId, File file, FileContent fileContent, String initiatorSlackId){
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, initiatorSlackId))
                .setApplicationName(APPLICATION_NAME).build();
        log.info("{} will be uploaded", fileId);
       File updated = service.files().update(fileId, file, fileContent).execute();
       log.info("{} updated", updated.getId());
    }

}
