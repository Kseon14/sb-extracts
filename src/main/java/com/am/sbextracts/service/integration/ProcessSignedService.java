package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import com.jayway.jsonpath.JsonPath;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.htmlcleaner.ContentNode;
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
import static com.am.sbextracts.service.integration.utils.ParsingUtils.isAktAndDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessSignedService implements Process {

    private static final String PROCESSED_ID_FILE_NAME = "processedId.log";
    private static final String PROCESSED_ID_FILE_NAME_PREFIX = "signed";

    @Value("${app.perRequestProcessingFilesCount}")
    private final int perRequestProcessingFilesCount;

    private final BambooHrSignedFileClient bambooHrSignedFile;
    private final HeaderService headerService;
    private final GDriveService gDriveService;
    private final ResponderService slackResponderService;

    @Override
    @SneakyThrows
    @SbExceptionHandler
    public void process(InternalSlackEventResponse slackEventResponse) {
        slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Start processing ....");
        gDriveService.validateFolderExistence(slackEventResponse.getGFolderId(), slackEventResponse.getInitiatorUserId());
        feign.Response response;
        Map<String, String> bchHeaders;
        try {
            bchHeaders = headerService.getBchHeaders(slackEventResponse.getSessionId(), slackEventResponse.getInitiatorUserId());
            response = bambooHrSignedFile.getSignedDocumentList(bchHeaders);
        } catch (RetryableException | IllegalArgumentException ex) {
            throw new SbExtractsException(ex.getMessage(), ex, slackEventResponse.getInitiatorUserId());
        }
        TagNode tagNode = getTagNode(response.body());

        final List<String> processedIds = new ArrayList<>();

        String logFileName = String.format("%s-%s-%s", slackEventResponse.getDate(), PROCESSED_ID_FILE_NAME_PREFIX,
                PROCESSED_ID_FILE_NAME);
        File file = null;
        long dateOfModification = -1;
        try {
            file = gDriveService.getFile(logFileName, slackEventResponse.getInitiatorUserId());
            if (file.exists()) {
                processedIds.addAll(Files.readAllLines(Paths.get(file.getPath())));
            }
            dateOfModification = file.lastModified();
            // find and filter out documents ids with specific date and "akt" in name
            List<String> ids = Arrays
                    .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName", true, false))
                    .filter(td -> isAktAndDate(td, slackEventResponse.getDate())).filter(ParsingUtils::isSigned)
                    .map(ProcessSignedService::getId).filter(id -> !CollectionUtils.containsAny(processedIds, id))
                    .collect(Collectors.toList());

            log.info("Documents for download: {}", ids.size());
            slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Documents for download: " + ids.size());
            for (int i = 0; i < Math.min(perRequestProcessingFilesCount, ids.size()); i++) {
                String id = ids.get(i);
                feign.Response report = bambooHrSignedFile
                        .getSignatureReport(bchHeaders, id);
                TagNode reportTag = getTagNode(report.body());

                Optional<? extends TagNode> tagNodeOptional = Arrays
                        .stream(reportTag.getElementsByAttValue("id", "js-signatureReportData", true, false)).findFirst();

                String jsonData = tagNodeOptional.map(t -> ((ContentNode) t.getAllChildren().get(0)).getContent())
                        .orElseThrow(() -> new IllegalArgumentException("not found content for js-signatureReportData"));

                String fileId = ((JSONArray) JsonPath.parse(jsonData).read("$..['most_recent_employee_file_data_id']"))
                        .get(0).toString();

                byte[] pdf = bambooHrSignedFile.getPdf(bchHeaders, fileId);
                String fileName = ((JSONArray) JsonPath.parse(jsonData).read("$..['original_file_name']")).get(0)
                        .toString();

                com.google.api.services.drive.model.File pdfFile = new com.google.api.services.drive.model.File();
                pdfFile.setName(fileName);
                pdfFile.setParents(Collections.singletonList(slackEventResponse.getGFolderId()));
                gDriveService.uploadFile(pdfFile, pdf, MediaType.APPLICATION_PDF_VALUE, slackEventResponse.getInitiatorUserId());
                log.info("Document uploaded [{}/{}]: {}", i + 1, Math.min(perRequestProcessingFilesCount, ids.size()), fileName);
                slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                        String.format("Document uploaded [%s/%s]: %s", i + 1, Math.min(perRequestProcessingFilesCount, ids.size()), fileName));

                FileUtils.writeStringToFile(file, id + "\r\n", StandardCharsets.UTF_8.toString(), true);
            }
        } catch (Throwable ex) {
            log.error("Error during download of acts", ex);
            throw new SbExtractsException("Error during download of acts:", ex, slackEventResponse.getInitiatorUserId());
        } finally {
            gDriveService.saveFile(file, dateOfModification, logFileName, slackEventResponse);
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
