package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import com.google.api.client.http.FileContent;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.TagNode;
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
import java.util.Optional;
import java.util.stream.Collectors;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.getTagNode;
import static com.am.sbextracts.service.integration.utils.ParsingUtils.isAktAndDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessSignedService implements Process {

    private final static String PROCESSED_ID_FILE_NAME = "processedId.log";
    private final static String PROCESSED_ID_FILE_NAME_PREFIX = "signed";

    private final BambooHrSignedFileClient bambooHrSignedFile;
    private final HeaderService headerService;
    private final GDriveService gDriveService;
    private final ResponderService slackResponderService;

    @Override
    @SneakyThrows
    @SbExceptionHandler
    public void process(InternalSlackEventResponse slackEventResponse) {
        slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Start processing ....");
        feign.Response response = bambooHrSignedFile
                .getSignedDocumentList(headerService.getBchHeaders(slackEventResponse.getSessionId(), slackEventResponse.getInitiatorUserId()));
        TagNode tagNode = getTagNode(response.body());

        final List<String> processedIds = new ArrayList<>();

        String logFileName = String.format("%s-%s-%s", slackEventResponse.getAktDate(), PROCESSED_ID_FILE_NAME_PREFIX,
                PROCESSED_ID_FILE_NAME);
        File file = null;
        try {
            file = gDriveService.getFile(logFileName, slackEventResponse.getInitiatorUserId());
            if (file.exists()) {
                processedIds.addAll(Files.readAllLines(Paths.get(file.getPath())));
            }

            // find and filter out documents ids with specific date and "akt" in name
            List<String> ids = Arrays
                    .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName", true, false))
                    .filter(td -> isAktAndDate(td, slackEventResponse.getAktDate())).filter(ParsingUtils::isSigned)
                    .map(ProcessSignedService::getId).filter(id -> !CollectionUtils.containsAny(processedIds, id))
                    .collect(Collectors.toList());

            log.info("Documents for download: {}", ids.size());
            slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Documents for download: " + ids.size());
            for (String id : ids) {
                feign.Response report = bambooHrSignedFile
                        .getSignatureReport(headerService.getBchHeaders(slackEventResponse.getSessionId(), slackEventResponse.getInitiatorUserId()), id);
                TagNode reportTag = getTagNode(report.body());

                Optional<? extends TagNode> tagNodeOptional = Arrays
                        .stream(reportTag.getElementsByAttValue("id", "js-signatureReportData", true, false)).findFirst();

                String jsonData = tagNodeOptional.map(t -> ((ContentNode) t.getAllChildren().get(0)).getContent())
                        .orElseThrow(() -> new IllegalArgumentException("not found content for js-signatureReportData"));

                String fileId = ((JSONArray) JsonPath.parse(jsonData).read("$..['most_recent_employee_file_data_id']"))
                        .get(0).toString();

                byte[] pdf = bambooHrSignedFile.getPdf(headerService.getBchHeaders(slackEventResponse.getSessionId(), slackEventResponse.getInitiatorUserId()), fileId);
                String fileName = ((JSONArray) JsonPath.parse(jsonData).read("$..['original_file_name']")).get(0)
                        .toString();

                com.google.api.services.drive.model.File pdfFile = new com.google.api.services.drive.model.File();
                pdfFile.setName(fileName);
                pdfFile.setParents(Collections.singletonList(slackEventResponse.getGFolderId()));
                gDriveService.uploadFile(pdfFile, pdf, MediaType.APPLICATION_PDF_VALUE, slackEventResponse.getInitiatorUserId());
                log.info("Document uploaded: {}", fileName);
                slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Documents uploaded: " + fileName);
                FileUtils.writeStringToFile(file, id + "\r\n", StandardCharsets.UTF_8.toString(), true);
            }
        } catch (Throwable ex) {
            log.error("Error during download of acts", ex);
            throw new SbExtractsException("Error during download of acts:", ex, slackEventResponse.getInitiatorUserId());

        } finally {
            if (file != null) {
                com.google.api.services.drive.model.File logFile = new com.google.api.services.drive.model.File();
                logFile.setName(logFileName);
                logFile.setMimeType(MediaType.TEXT_PLAIN_VALUE);

                if (StringUtils.equals(file.getName(), logFileName)) {
                    logFile.setParents(Collections.singletonList(slackEventResponse.getGFolderId()));
                    gDriveService.uploadFile(logFile, FileUtils.readFileToByteArray(file), MediaType.TEXT_PLAIN_VALUE, slackEventResponse.getInitiatorUserId());
                } else {
                    gDriveService.updateFile(file.getName(), logFile, new FileContent(MediaType.TEXT_PLAIN_VALUE, file), slackEventResponse.getInitiatorUserId());
                }

                slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Google report updated");

                log.info("Local report deleted: {}", file.delete());
                log.info("Report updated: {}", file.getName());
                log.info("DONE");
                slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Done");
            }
        }
    }

    private static String getId(TagNode tagNode) {
        Optional<String> href = Arrays
                .stream(tagNode.getElementsByAttValue("class", "ReportsTable__reportNameText", true, false)).findFirst()
                .map(at -> at.getAttributeByName("href"));

        String[] splitResult = href.map(t -> t.split("="))
                .orElseThrow(() -> new IllegalArgumentException("can not extract id"));
        return splitResult[1];
    }
}
