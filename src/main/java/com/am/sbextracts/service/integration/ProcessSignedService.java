package com.am.sbextracts.service.integration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.model.ReportData;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;

import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.isActorReconciliationAndDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessSignedService implements Process {

  private static final String PROCESSED_ID_FILE_NAME = "processedId.log";
  private static final String PROCESSED_ID_FILE_NAME_PREFIX = "signed";

  @Value("${app.perRequestProcessingFilesCount}")
  private final int perRequestProcessingFilesCount;

  @Value("${COMPANY_NAME}")
  private final String company;

  private final BambooHrSignedFileClient bambooHrSignedFile;
  private final HeaderService headerService;
  private final GDriveService gDriveService;
  private final ResponderService slackResponderService;

  @Override
  @SbExceptionHandler
  public void process(InternalSlackEventResponse slackEventResponse) {
    final String initiatorUserId = slackEventResponse.getInitiatorUserId();
    gDriveService.validateFolderExistence(slackEventResponse.getGFolderId(), initiatorUserId);
    slackResponderService.log(initiatorUserId, "Start processing ....");
    BambooHrSignedFileClient.SignedDocument rootDocument;
    Map<String, String> bchHeaders;
    try {
      bchHeaders = headerService.getBchHeaders(slackEventResponse.getSessionId(), initiatorUserId);
      rootDocument = bambooHrSignedFile.getSignedDocuments(bchHeaders);
    } catch (RetryableException | IllegalArgumentException ex) {
      throw new SbExtractsException(ex.getMessage(), ex, initiatorUserId);
    }

    final List<String> processedIds = new ArrayList<>();

    String logFileName = String.format("%s-%s-%s", slackEventResponse.getDate(), PROCESSED_ID_FILE_NAME_PREFIX,
        PROCESSED_ID_FILE_NAME);
    File file = null;
    try {
      file = gDriveService.getFileOrCreateNew(logFileName, slackEventResponse.getGFolderId(), initiatorUserId);
      if (file.exists()) {
        processedIds.addAll(Files.readAllLines(Paths.get(file.getPath())));
      }

      List<String> ids = rootDocument.getDocuments().stream()
          .filter(Objects::nonNull)
          .filter(doc -> isActorReconciliationAndDate(doc, slackEventResponse.getDate()))
          .filter(ParsingUtils::isSigned)
          .map(BambooHrSignedFileClient.Document::getId)
          .map(String::valueOf)
          .filter(id -> !CollectionUtils.containsAny(processedIds, id))
          .collect(Collectors.toList());

      log.info("Documents for download: {}", ids.size());
      slackResponderService.log(initiatorUserId, "Documents for download: " + ids.size());

      for (int i = 0; i < Math.min(perRequestProcessingFilesCount, ids.size()); i++) {
        String id = ids.get(i);
        ReportData report = bambooHrSignedFile.getSignatureReport(bchHeaders, id);

        String fileId = null;
        String fileName = null;
        for (Integer reportId : report.getReportByIds()) {
          ReportData.TemplateData templateData = report.getTemplateSignatureReport()
              .get(String.valueOf(reportId));
          if (templateData.getStatus().equals("Completed")) {
            ReportData.EmployeeFile employeeFile = templateData.getEmployeeFile();
            fileId = employeeFile.getMostRecentEmployeeFileDataId();
            fileName = employeeFile.getOriginalFileName();
            break;
          }
        }

        if (fileId == null || fileName == null) {
          log.info("skipping file due to missed id or file name {}", id);
          slackResponderService.sendErrorMessageToInitiator(initiatorUserId, "error",
              "skipping file due to missed id or file name");
          continue;
        }
        log.info("file {} : {} will be processed", fileId, fileName);

        byte[] pdf = bambooHrSignedFile.getPdf(bchHeaders, fileId);
        com.google.api.services.drive.model.File pdfFile = new com.google.api.services.drive.model.File();
        pdfFile.setName(fileName);
        pdfFile.setParents(Collections.singletonList(slackEventResponse.getGFolderId()));
        gDriveService.uploadFile(pdfFile, pdf, MediaType.APPLICATION_PDF_VALUE,
            initiatorUserId);
        log.info("Document uploaded [{}/{}]: {}", i + 1, Math.min(perRequestProcessingFilesCount, ids.size()),
            fileName);
        slackResponderService.log(initiatorUserId,
            String.format("Document uploaded [%s/%s]: %s", i + 1, Math.min(perRequestProcessingFilesCount,
                ids.size()), fileName));

        FileUtils.writeStringToFile(file, id + "\r\n", StandardCharsets.UTF_8.toString(), true);
      }
    } catch (Throwable ex) {
      log.error("Error during download of acts", ex);
      throw new SbExtractsException("Error during download of acts:", ex, initiatorUserId);
    } finally {
      gDriveService.saveFile(file, logFileName, slackEventResponse);
    }
  }
}
