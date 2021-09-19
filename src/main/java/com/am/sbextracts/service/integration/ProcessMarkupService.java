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

    private final static int ATTEMPTS_LIMIT = 8;
    private final static int ATTEMPTS_DELAY = 4;
    private final static int DEFAULT_DELAY = 2;
    private final static String PROPERTIES_FILE = "signature-markup.properties";
    private final static String PROCESSED_ID_FILE_NAME = "processedId.log";
    private final static String PROCESSED_ID_FILE_NAME_PREFIX = "markup";

    @Value("${app.perRequestProcessingFilesCount}")
    private final int perRequestProcessingFilesCount;

    private final static Properties prop = new Properties();

    private final ObjectMapper mapper;
    private final ReportService reportService;
    private final BambooHrSignClient bambooHrSignClient;
    private final HeaderService headerService;
    private final GDriveService gDriveService;
    private final ResponderService slackResponderService;

    @SbExceptionHandler
    @SneakyThrows
    @Override
    public void process(InternalSlackEventResponse slackEventResponse) {
        slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Starting....");
        Map<String, String> employees = reportService.getEmployees();
        int fileCount;
        var offset = 0;

        final List<String> processedIds = new ArrayList<>();
        File file = null;
        String logFileName = String.format("%s-%s-%s", slackEventResponse.getDate(), PROCESSED_ID_FILE_NAME_PREFIX,
                PROCESSED_ID_FILE_NAME);
        try {
            file = gDriveService.getFile(logFileName, slackEventResponse.getInitiatorUserId());
            if (file.exists()) {
                processedIds.addAll(Files.readAllLines(Paths.get(file.getPath())));
            }
            do {
                Folder folder = getFolderContent(slackEventResponse, offset);
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

                for (int i = 0; i < Math.min(perRequestProcessingFilesCount, infos.size()); i++) {
                    var info = infos.get(i);
                    String employeeInternalId = employees.get(info.getInn());
                    log.info("Start processing  [{}/{}]: {}....", i + 1,
                            Math.min(perRequestProcessingFilesCount, infos.size()), info.getInn());
                    slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                            String.format("Start processing [%s/%s]: %s...", i + 1,
                                    Math.min(perRequestProcessingFilesCount, infos.size()), info.getInn()));
                    processDocuments(employeeInternalId, info, slackEventResponse, file);
                }
                offset = folder.getOffset();
                fileCount = folder.getSectionFileCount();
            } while (offset < fileCount);
        } catch (Throwable ex) {
            log.error("Error during markup of acts", ex);
            throw new SbExtractsException("Error during markup of acts:", ex, slackEventResponse.getInitiatorUserId());
        } finally {
            gDriveService.saveFile(file, logFileName, slackEventResponse);
        }
    }

    private Folder getFolderContent(InternalSlackEventResponse slackEventResponse, int offset) {
        return bambooHrSignClient.getFolderContent(headerService.getBchHeaders(slackEventResponse.getSessionId(),
                        slackEventResponse.getInitiatorUserId()),
                BambooHrSignClient.FolderParams.of(slackEventResponse.getFolderId(), offset));
    }

    @SneakyThrows
    private void processDocuments(String employeeInternalId, DocumentInfo info,
                                  InternalSlackEventResponse slackEventResponse, File file) {
        Response updateTemplateResponse = convert(
                bambooHrSignClient.createTemplate(headerService.getBchHeaders(slackEventResponse.getSessionId(),
                                slackEventResponse.getInitiatorUserId()),
                        new BambooHrSignClient.CrtRequest(true, info.getTemplateFileId())));
        log.info("updateTemplateResponse {}", updateTemplateResponse.isSuccess());
        if (updateTemplateResponse.isSuccess()) {
            Response isReady;
            int attempts = 0;
            do {
                isReady = bambooHrSignClient.isReady(headerService.getBchHeaders(slackEventResponse.getSessionId(),
                                slackEventResponse.getInitiatorUserId()),
                        BambooHrSignClient.CompleteParams.of(info.getFileId()));
                TimeUnit.SECONDS.sleep(ATTEMPTS_DELAY);
                attempts++;
            } while (!isReady.isSuccess() && attempts <= ATTEMPTS_LIMIT);

            log.info("isReady {}", isReady.isSuccess());
            if (isReady.isSuccess()) {
                TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
                Response completed = bambooHrSignClient.getCompleted(headerService.getBchHeaders(slackEventResponse.getSessionId(),
                                slackEventResponse.getInitiatorUserId()),
                        BambooHrSignClient.CompleteParams.of(info.getFileId()));
                log.info("updateTemplateResponse {}", updateTemplateResponse.isSuccess());
                if (completed.isSuccess()) {
                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
                    int pagesCount = getPdfPagesCount(slackEventResponse, info.getTemplateFileId());
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
                            convert(bambooHrSignClient.updateTemplate(headerService.getBchHeaders(slackEventResponse.getSessionId(),
                                            slackEventResponse.getInitiatorUserId()),
                                    new BambooHrSignClient.UpdateRequest(true,
                                            Integer.parseInt(completed.getEsignatureTemplateId()), markUp)));
                    log.info("Response {}, error {}, message {}", update.isSuccess(), update.getError(),
                            update.getMessage());

                    Map<String, Object> signParams = Map.of("allIds", "off", "message",
                            "Please take a moment to sign this document", "employeeIds[]", employeeInternalId);
                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
                    Response signatureRequest = convert(bambooHrSignClient.signatureRequest(
                            headerService.getBchHeaders(slackEventResponse.getSessionId(), slackEventResponse.getInitiatorUserId()),
                            signParams, update.getWorkflowId()));
                    log.info("Document for inn: {}, error:{}, success: {}", info.getInn(), signatureRequest.getError(),
                            signatureRequest.isSuccess());
                    slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                            String.format("Document for inn: %s, %s, success: %s", info.getInn(), signatureRequest.getError(),
                                    signatureRequest.isSuccess()));
                    FileUtils.writeStringToFile(file, info.getFileId() + "\r\n", StandardCharsets.UTF_8.toString(), true);

                }
            }
        }
    }


    @SneakyThrows
    private Response convert(feign.Response response) {
        return mapper.readValue(response.body().asInputStream(), Response.class);
    }

    @SneakyThrows
    private int getPdfPagesCount(InternalSlackEventResponse slackEventResponse, int templateFileId) {
        byte[] pdf = bambooHrSignClient.getPdf(headerService.getBchHeaders(slackEventResponse.getSessionId(),
                slackEventResponse.getInitiatorUserId()), templateFileId);
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
