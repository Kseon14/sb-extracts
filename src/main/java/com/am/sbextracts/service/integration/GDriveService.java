package com.am.sbextracts.service.integration;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static com.am.sbextracts.service.integration.GAuthService.APPLICATION_NAME;
import static com.am.sbextracts.service.integration.GAuthService.JSON_FACTORY;

@Slf4j
@Service
@RequiredArgsConstructor
public class GDriveService {
    private static final int MAX_RETRY = 2;

    private final GAuthService gAuthService;

    private final ResponderService slackResponderService;
    public void uploadFile(File fileMetadata, byte[] fileBody, String type, String initiatorSlackId) {
        ByteArrayContent mediaContent = new ByteArrayContent(type, fileBody);
        File file;
        try {
            file = getService(initiatorSlackId).files().create(fileMetadata, mediaContent).setFields("id").execute();
            log.info("File ID: {}", file.getId());
        } catch (GoogleJsonResponseException ex) {
            if (ex.getStatusCode() == 401) {
                gAuthService.reAuth(initiatorSlackId);
                uploadFile(fileMetadata, fileBody, type, initiatorSlackId);
            }
            throw new IllegalStateException(ex);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

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
                gAuthService.reAuth(initiatorSlackId);
            }
        };
    }

    private Drive getService(String initiatorSlackId) {
        return new Drive.Builder(GAuthService.getNetHttpTransport(), JSON_FACTORY,
                gAuthService.getCredentials(initiatorSlackId, false))
                .setApplicationName(APPLICATION_NAME).build();
    }

    private static int circuitBreaker(int attempt, BiConsumer<String, String> consumer, String fileId, String initiatorSlackId) {
        try {
            if (attempt <= MAX_RETRY) {
                consumer.accept(fileId, initiatorSlackId);
            }
            return attempt + 1;
        } catch (Exception ex) {
            GAuthService.removeToken(initiatorSlackId);
            return circuitBreaker(attempt + 1, consumer, fileId, initiatorSlackId);
        }
    }

    public java.io.File getFileOrCreateNew(String fileName, String initiatorSlackId) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Drive service = getService(initiatorSlackId);
        List<File> result;
        try {
            result = service
                    .files()
                    .list()
                    .setSpaces("drive")
                    .setPageSize(20)
                    .setQ("name = '" + fileName + "' and trashed = false")
                    .setFields("files(id, name)")
                    .execute().getFiles();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        if (CollectionUtils.isEmpty(result)) {
            log.info("file not found in gDrive, new file locally will be created with name {}", fileName);
            return new java.io.File(fileName);
        }
        if (result.size() > 1) {
            throw new IllegalArgumentException("too many " + fileName + " files with the same name in gDrive");
        }

        String intName = result.get(0).getId();
        log.info("gFile exists and downloaded, content will be saved locally with name {}", intName);
        java.io.File file = new java.io.File(intName);
        try (OutputStream outputStream = new FileOutputStream(file)) {
            service.files().get(intName)
                    .executeMediaAndDownloadTo(byteArrayOutputStream);
            byteArrayOutputStream.writeTo(outputStream);
            log.info("gFile content saved in local file");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return file;
    }

    public void saveFile(java.io.File file, long dateOfModification, String logFileName, InternalSlackEventResponse slackEventResponse) {
        saveFile(file, dateOfModification, logFileName, slackEventResponse.getInitiatorUserId(), slackEventResponse.getGFolderId());
    }

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

    public void updateFile(String fileId, File file, FileContent fileContent, String initiatorSlackId) {
        log.info("{} will be uploaded", fileId);
        File updated = null;
        try {
            updated = getService(initiatorSlackId).files().update(fileId, file, fileContent).execute();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        log.info("{} uploaded", updated.getId());
    }

}
