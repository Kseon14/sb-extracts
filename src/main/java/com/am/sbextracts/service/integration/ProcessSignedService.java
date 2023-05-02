package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import feign.RetryableException;
import feign.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.htmlcleaner.TagNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.getTagNode;
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
        feign.Response response;
        Map<String, String> bchHeaders;
        try {
            bchHeaders = headerService.getBchHeaders(slackEventResponse.getSessionId(), initiatorUserId);
            response = bambooHrSignedFile.getSignedDocumentList(bchHeaders);
        } catch (RetryableException | IllegalArgumentException ex) {
            throw new SbExtractsException(ex.getMessage(), ex, initiatorUserId);
        }
        TagNode tagNode = getTagNode(response.body());

        final List<String> processedIds = new ArrayList<>();

        String logFileName = String.format("%s-%s-%s", slackEventResponse.getDate(), PROCESSED_ID_FILE_NAME_PREFIX,
                PROCESSED_ID_FILE_NAME);
        File file = null;
        try {
            file = gDriveService.getFileOrCreateNew(logFileName, slackEventResponse.getGFolderId(), initiatorUserId);
            if (file.exists()) {
                processedIds.addAll(Files.readAllLines(Paths.get(file.getPath())));
            }
            // find and filter out documents ids with specific date and "akt" in name
            List<String> ids = Arrays
                    .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName", true, false))
                    .filter(td -> isActorReconciliationAndDate(td, slackEventResponse.getDate())).filter(ParsingUtils::isSigned)
                    .map(ProcessSignedService::getId).filter(id -> !CollectionUtils.containsAny(processedIds, id))
                    .collect(Collectors.toList());

            if (ids.size() == 0) {
                log.info("No files to download");
                slackResponderService.log(initiatorUserId, "No files to download");
                gDriveService.saveFile(file, logFileName, slackEventResponse);
                log.info("Local report deleted: {}", file.delete());
            } else {
                log.info("Documents for download: {}", ids.size());
                slackResponderService.log(initiatorUserId, "Documents for download: " + ids.size());
            }

            for (int i = 0; i < Math.min(perRequestProcessingFilesCount, ids.size()); i++) {
                String id = ids.get(i);
                feign.Response report = bambooHrSignedFile.getSignatureReport(bchHeaders, id);

                String jsonData = Util.toString(report.body().asReader(StandardCharsets.UTF_8));
                DocumentContext parseResult = JsonPath.parse(jsonData);
                String fileId = getField(parseResult, "$..['most_recent_employee_file_data_id']", id,
                        initiatorUserId);
                if (fileId == null) {
                    log.info("skipping file...");
                    slackResponderService.sendErrorMessageToInitiator(initiatorUserId, "error", "skipping file...");
                    continue;
                }

                String fileName = getField(parseResult, "$..['original_file_name']", id, initiatorUserId);
                if (fileName == null){
                    log.info("skipping file...");
                    slackResponderService.sendErrorMessageToInitiator(initiatorUserId, "error", "skipping file...");
                    continue;
                }

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

    private String getField(final DocumentContext parseResult,
                            final String fieldToFind,
                            final String id,
                            final String initiatorUserId) {
        final JSONArray jsonArray = parseResult.read(fieldToFind);
        if (jsonArray.isEmpty()){
            final String errorMessage = String.format("Sign report is not valid https://%s.bamboohr.com/reports/esignatures/?id=%s",
                    company, id);
            log.error(errorMessage);
            slackResponderService.sendErrorMessageToInitiator(initiatorUserId, "error", errorMessage);
            return null;
        }
        return jsonArray.get(0).toString();
    }

    private static String getId(TagNode tagNode) {
        Optional<String> href = Arrays
                .stream(tagNode.getElementsByAttValue("class", "ReportsTable__reportNameText",
                        true, false)).findFirst()
                .map(at -> at.getAttributeByName("href"));

        String[] splitResult = href.map(t -> t.split("="))
                .orElseThrow(() -> new IllegalArgumentException("can not extract id"));
        return splitResult[1];
    }
}
