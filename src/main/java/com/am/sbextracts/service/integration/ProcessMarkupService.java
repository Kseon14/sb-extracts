package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrSignClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.DocumentInfo;
import com.am.sbextracts.model.Folder;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.model.Response;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.isAkt;
import static com.am.sbextracts.service.integration.utils.ParsingUtils.isRequiredTag;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMarkupService implements Process {

    private static final int ATTEMPTS_LIMIT = 8;
    private static final int ATTEMPTS_DELAY = 4;
    private static final int DEFAULT_DELAY = 2;
    private static final String PROPERTIES_FILE = "signature-markup.properties";
    private static final String PROCESSED_ID_FILE_NAME = "processedId.log";
    private static final String PROCESSED_ID_FILE_NAME_PREFIX = "markup";

    @Value("${app.perRequestProcessingFilesCount}")
    private final int perRequestProcessingFilesCount;

    private final static Properties prop = new Properties();

    private final ObjectMapper mapper;
    private final ReportService reportService;
    private final BambooHrSignClient bambooHrSignClient;
    private final HeaderService headerService;
    private final GDriveService gDriveService;
    private final ResponderService slackResponderService;
    private final SignClientCommon signClientCommon;

    @SbExceptionHandler
    @SneakyThrows
    @Override
    public void process(InternalSlackEventResponse slackEventResponse) {
        slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Starting....");
        gDriveService.validateFolderExistence(slackEventResponse.getGFolderId(), slackEventResponse.getInitiatorUserId());
        Map<String, String> employees = reportService.getEmployees();
        int fileCount;
        var offset = 0;
        var globalCounter = 0;

        final List<String> processedIds = new ArrayList<>();
        File file = null;
        String logFileName = String.format("%s-%s-%s", slackEventResponse.getDate(), PROCESSED_ID_FILE_NAME_PREFIX,
                PROCESSED_ID_FILE_NAME);
        long dateOfModification = -1;
        try {
            file = gDriveService.getFile(logFileName, slackEventResponse.getInitiatorUserId());
            if (file.exists()) {
                processedIds.addAll(Files.readAllLines(Paths.get(file.getPath())));
            }
            dateOfModification = file.lastModified();
            Map<String, String> bchHeaders = headerService.getBchHeaders(slackEventResponse.getSessionId(),
                    slackEventResponse.getInitiatorUserId());
            do {
                Folder folder = signClientCommon.getFolderContent(offset, slackEventResponse.getFolderId(), bchHeaders);
                if (folder == null) {
                    throw new SbExtractsException("folder content is null, possible you are not logged in, please refresh your bamboo session",
                            slackEventResponse.getInitiatorUserId());
                }
                log.info("start processing folder: {}, filesCount: {}, offset: {}", folder.getSectionName(),
                        folder.getSectionFileCount(), offset);
                TagNode tagNode = new HtmlCleaner().clean(folder.getHtml());

                List<DocumentInfo> infos = tagNode.getElementListByName("button", true)
                        .stream()
                        .filter(isRequiredTag)
                        .filter(isAkt)
                        .map(b -> DocumentInfo.of(ParsingUtils.getInn(b),
                                ParsingUtils.getFileId(b), ParsingUtils.getTemplateFileId(b)))
                        .filter(info -> !CollectionUtils.containsAny(processedIds, info.getFileId() + ""))
                        .filter(info -> employees.get(info.getInn()) != null)
                        .collect(Collectors.toList());
                log.info("Following Documents count {} will be processed", infos.size());
                slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                        String.format("Following Documents count %s will be processed", infos.size()));

                for (int i = 0; i < infos.size(); i++) {
                    var info = infos.get(i);
                    String employeeInternalId = employees.get(info.getInn());
                    log.info("Start processing  [{}/{}] already processed: [{}]: {}....",
                            i + 1,
                            infos.size(),
                            globalCounter,
                            info.getInn());
                    slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                            String.format("Start processing [%s/%s] already processed: [%s]: %s...",
                                    i + 1,
                                    infos.size(),
                                    globalCounter,
                                    info.getInn()));
                    processDocuments(employeeInternalId, info, slackEventResponse, file, bchHeaders);
                    globalCounter++;
                    if (globalCounter > perRequestProcessingFilesCount) {
                        return;
                    }
                }
                offset = folder.getOffset();
                fileCount = folder.getSectionFileCount();
            } while (offset < fileCount);
        } catch (Throwable ex) {
            throw new SbExtractsException("Error during markup of acts", ex, slackEventResponse.getInitiatorUserId());
        } finally {
            //file not exist in gdrive and not updated -> dateOfModification = 0 and last mod file = false (0)   -> false
            //file not exist in gdrive and not updated -> dateOfModification = 0 and last mod file = true (124)   -> true
            //file exist in gdrive and not updated -> dateOfModification = 1234 and last mod file = false (1234)  -> false
            //file exist on gdrive and updated -> dateOfModification = 1234 and last mod file = true (5678)  -> true
            gDriveService.saveFile(file, dateOfModification, logFileName, slackEventResponse);
        }
    }

    @SneakyThrows
    private void processDocuments(String employeeInternalId, DocumentInfo info,
                                  InternalSlackEventResponse slackEventResponse, File file, Map<String, String> bchHeaders) {

        Response updateTemplateResponse = convert(
                bambooHrSignClient.createTemplate(bchHeaders,
                        new BambooHrSignClient.CrtRequest(true, info.getTemplateFileId())));
        log.info("updateTemplateResponse {}", updateTemplateResponse.isSuccess());
        if (updateTemplateResponse.isSuccess()) {
            Response isReady;
            int attempts = 0;
            do {
                isReady = bambooHrSignClient.isReady(bchHeaders,
                        BambooHrSignClient.CompleteParams.of(info.getFileId()));
                TimeUnit.SECONDS.sleep(ATTEMPTS_DELAY);
                attempts++;
            } while (!isReady.isSuccess() && attempts <= ATTEMPTS_LIMIT);

            log.info("isReady {}", isReady.isSuccess());
            if (isReady.isSuccess()) {
                TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
                Response completed = bambooHrSignClient.getCompleted(bchHeaders,
                        BambooHrSignClient.CompleteParams.of(info.getFileId()));
                log.info("updateTemplateResponse {}", completed.isSuccess());
                if (completed.isSuccess()) {
                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
                    int pagesCount = getPdfPagesCount(info.getTemplateFileId(), bchHeaders);
                    if (pagesCount == 0) {
                        log.error("File download problem for inn {}", info.getInn());
                        slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                                String.format("File download problem for inn %s", info.getInn()));
                        return;
                    }
                    String markUp = getMarkUp(pagesCount);
                    if (StringUtils.isBlank(markUp)) {
                        log.error("markup source file read problem");
                        slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                                String.format("markup source file read problem for inn %s", info.getInn()));
                        return;
                    }
                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
                    Response update =
                            convert(bambooHrSignClient.updateTemplate(bchHeaders,
                                    new BambooHrSignClient.UpdateRequest(true,
                                            Integer.parseInt(completed.getEsignatureTemplateId()), markUp)));
                    log.debug("Response {}, error {}, message {}", update.isSuccess(), update.getError(),
                            update.getMessage());

                    Map<String, Object> signParams = Map.of("allIds", "off", "message",
                            "Please take a moment to sign this document", "employeeIds[]", employeeInternalId);
                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
                    Response signatureRequest = convert(bambooHrSignClient.signatureRequest(
                            bchHeaders,
                            signParams, update.getWorkflowId()));
                    log.info("Document for inn: {}, error:{}, success: {}", info.getInn(), signatureRequest.getError(),
                            signatureRequest.isSuccess());
                    slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                            String.format("Document for inn: %s, %s, success: %s", info.getInn(), signatureRequest.getError(),
                                    signatureRequest.isSuccess()));
                    FileUtils.writeStringToFile(file, info.getFileId() + "\r\n", StandardCharsets.UTF_8.toString(), true);

                }
            }
        } else {
            slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                    String.format("Update Template action is failed for inn %s skipping...", info.getInn()));
        }
    }

    @SneakyThrows
    private Response convert(feign.Response response) {
        return mapper.readValue(response.body().asInputStream(), Response.class);
    }

    @SneakyThrows
    private int getPdfPagesCount(int templateFileId, Map<String, String> bchHeaders) {
        byte[] pdf = bambooHrSignClient.getPdf(bchHeaders, templateFileId);
        if (pdf.length == 0) {
            return 0;
        }
        try (PDDocument doc = PDDocument.load(pdf)) {
            doc.getPage(0);
            return doc.getNumberOfPages();
        }
    }

    private String getMarkUp(int pagesCount) throws IOException {
        if (prop.isEmpty()) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
                if (input == null) {
                    throw new FileNotFoundException("Resource not found: " + PROPERTIES_FILE);
                }
                prop.load(input);
            }
        }
        return prop.getProperty(pagesCount + "");
    }
}
