package com.am.sbextracts.service.integration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.am.sbextracts.client.BambooHrSignClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.DocumentInfo;
import com.am.sbextracts.model.FieldWrapper;
import com.am.sbextracts.model.Folder;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.model.Response;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.FILTER_BY_DATE_AND_DOCUMENT_TYPE;
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

  private static final Properties prop = new Properties();

  private final ObjectMapper mapper;
  private final ReportService reportService;
  private final BambooHrSignClient bambooHrSignClient;
  private final HeaderService headerService;
  private final GDriveService gDriveService;
  private final ResponderService slackResponderService;
  private final SignClientCommon signClientCommon;

  @SbExceptionHandler
  @Override
  public void process(InternalSlackEventResponse slackEventResponse) {
    final String initiatorUserId = slackEventResponse.getInitiatorUserId();
    slackResponderService.log(initiatorUserId, "Starting....");
    gDriveService.validateFolderExistence(slackEventResponse.getGFolderId(), initiatorUserId);
    Map<String, String> employees = reportService.getEmployees(initiatorUserId);
    int fileCount;
    var offset = 0;
    var globalCounter = 0;

    final List<String> processedIds = new ArrayList<>();
    File file = null;
    String logFileName = String.format("%s-%s-%s", slackEventResponse.getDate(), PROCESSED_ID_FILE_NAME_PREFIX,
        PROCESSED_ID_FILE_NAME);
    try {
      file = gDriveService.getFileOrCreateNew(logFileName, slackEventResponse.getGFolderId(), initiatorUserId);
      if (file.exists()) {
        processedIds.addAll(Files.readAllLines(Paths.get(file.getPath())));
      }
      Map<String, String> bchHeaders = headerService.getBchHeaders(slackEventResponse.getSessionId(),
          initiatorUserId);
      do {
        Folder folder = signClientCommon.getFolderContent(offset, slackEventResponse.getFolderId(), bchHeaders);
        if (folder == null) {
          throw new SbExtractsException(
              "folder content is null, possible you are not logged in, please refresh your bamboo session",
              initiatorUserId);
        }
        log.info("start processing folder: {}, filesCount: {}, offset: {}", folder.getSectionName(),
            folder.getSectionFileCount(), offset);
        TagNode tagNode = new HtmlCleaner().clean(folder.getHtml());

        List<DocumentInfo> infos = tagNode.getElementListByName("button", true)
            .stream()
            .filter(isRequiredTag)
            .filter(tag -> FILTER_BY_DATE_AND_DOCUMENT_TYPE.test(tag, slackEventResponse))
            .map(b -> DocumentInfo.of(ParsingUtils.getInn(b),
                ParsingUtils.getFileId(b), ParsingUtils.getTemplateFileId(b),
                ParsingUtils.isMarkedBySpecialSymbols(b)))
            .filter(info -> !CollectionUtils.containsAny(processedIds, info.getFileId() + ""))
            .filter(info -> employees.get(info.getInn()) != null)
            .collect(Collectors.toList());

        log.info("Following Documents {} count {} will be processed", slackEventResponse.getTypeOfDocuments(), infos.size());
        slackResponderService.log(initiatorUserId,
            String.format("Following Documents %s count %s will be processed",
                Arrays.toString(slackEventResponse.getTypeOfDocuments()), infos.size()));

        for (int i = 0; i < infos.size(); i++) {
          var info = infos.get(i);
          String employeeInternalId = employees.get(info.getInn());
          log.info("Start processing  [{}/{}] already processed: [{}]: {}....",
              i + 1,
              infos.size(),
              globalCounter,
              info.getInn());
          slackResponderService.log(initiatorUserId,
              String.format("Start processing [%s/%s] already processed: [%s]: %s...",
                  i + 1,
                  infos.size(),
                  globalCounter,
                  info.getInn()));
          processDocuments(employeeInternalId, info, slackEventResponse, file, bchHeaders);
          globalCounter++;
          if (globalCounter >= perRequestProcessingFilesCount) {
            return;
          }
        }
        //gDriveService.saveFile(file, logFileName, slackEventResponse, false);
        offset = folder.getOffset();
        fileCount = folder.getSectionFileCount();
      } while (offset < fileCount);
    } catch (Throwable ex) {
      throw new SbExtractsException("Error during markup of acts", ex, initiatorUserId);
    } finally {
      gDriveService.saveFile(file, logFileName, slackEventResponse);
    }
  }

  private void processDocuments(String employeeInternalId,
      DocumentInfo info,
      InternalSlackEventResponse slackEventResponse,
      File file,
      Map<String, String> bchHeaders) {

    Response updateTemplateResponse = convert(
        bambooHrSignClient.createTemplate(bchHeaders,
            new BambooHrSignClient.CrtRequest(true, info.getTemplateFileId())));
    log.info("updateTemplateResponse {}", updateTemplateResponse.isSuccess());
    String initiatorUserId = slackEventResponse.getInitiatorUserId();
    if (updateTemplateResponse.isSuccess()) {
      Response isReady;
      int attempts = 0;
      do {
        isReady = bambooHrSignClient.isReady(bchHeaders,
            BambooHrSignClient.CompleteParams.of(info.getFileId()));
        try {
          TimeUnit.SECONDS.sleep(ATTEMPTS_DELAY);
        } catch (InterruptedException e) {
          log.error("sleep was interrupted", e);
          Thread.currentThread().interrupt();
        }
        attempts++;
      } while (!isReady.isSuccess() && attempts <= ATTEMPTS_LIMIT);

      log.info("isReady {}", isReady.isSuccess());
      if (isReady.isSuccess()) {
        try {
          TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
        } catch (InterruptedException e) {
          log.error("sleep was interrupted", e);
          Thread.currentThread().interrupt();
        }
        Response completed = bambooHrSignClient.getCompleted(bchHeaders,
            BambooHrSignClient.CompleteParams.of(info.getFileId()));
        log.info("updateTemplateResponse {}", completed.isSuccess());
        if (completed.isSuccess()) {
          try {
            TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
          } catch (InterruptedException e) {
            log.error("sleep was interrupted", e);
            Thread.currentThread().interrupt();
          }
          String markUp = getMarkUp(info, bchHeaders, initiatorUserId);
          if (markUp == null) {
            return;
          }
          try {
            TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
          } catch (InterruptedException e) {
            log.error("sleep was interrupted", e);
            Thread.currentThread().interrupt();
          }
          Response update =
              convert(bambooHrSignClient.updateTemplate(bchHeaders,
                  new BambooHrSignClient.UpdateRequest(true,
                      Integer.parseInt(completed.getEsignatureTemplateId()), markUp)));
          log.debug("Response {}, error {}, message {}", update.isSuccess(), update.getError(),
              update.getMessage());

          Map<String, Object> signParams = Map.of("allIds", "off", "message",
              "Please take a moment to sign this document", "employeeIds[]", employeeInternalId);
          try {
            TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
          } catch (InterruptedException e) {
            log.error("sleep was interrupted", e);
            Thread.currentThread().interrupt();
          }
          Response signatureRequest = convert(bambooHrSignClient.signatureRequest(
              bchHeaders,
              signParams, update.getWorkflowId()));
          log.info("Document for inn: {}, error:{}, success: {}", info.getInn(), signatureRequest.getError(),
              signatureRequest.isSuccess());
          slackResponderService.log(initiatorUserId,
              String.format("Document for inn: %s, %s, success: %s", info.getInn(), signatureRequest.getError(),
                  signatureRequest.isSuccess()));
          try {
            FileUtils.writeStringToFile(file, info.getFileId() + "\r\n", StandardCharsets.UTF_8.toString(), true);
          } catch (IOException e) {
            throw new SbExtractsException("Error during writing to file", e, initiatorUserId);
          }

        }
      }
    } else {
      slackResponderService.log(initiatorUserId,
          String.format("Update Template action is failed for inn %s skipping...", info.getInn()));
    }
  }

  @Nullable
  private String getMarkUp(DocumentInfo info, Map<String, String> bchHeaders, String initiatorUserId) {
    byte[] pdf = bambooHrSignClient.getPdf(bchHeaders, info.getTemplateFileId());
    if (pdf.length == 0) {
      return null;
    }
    if (info.isMarkedBySpecialSymbols()) {
      try {
        return getMarkUpForReconciliation(pdf);
      } catch (Exception ex) {
        log.error("Error during determination of coordinates", ex);
        slackResponderService.log(initiatorUserId,
            String.format("Error during determination of coordinates for file with inn: %s", info.getInn()));
        return null;
      }
    }

    int pagesCount = getPdfPagesCount(pdf, initiatorUserId);
    if (pagesCount == 0) {
      log.error("File download problem for inn {}", info.getInn());
      slackResponderService.log(initiatorUserId,
          String.format("File download problem for inn %s", info.getInn()));
      return null;
    }
    String markUp;
    try {
      markUp = getMarkUp(pagesCount);
    } catch (IOException ex) {
      throw new SbExtractsException("Error during prop retrieving", ex, initiatorUserId);
    }
    if (StringUtils.isBlank(markUp)) {
      log.error("markup source file read problem");
      slackResponderService.log(initiatorUserId,
          String.format("markup source file read problem for inn %s", info.getInn()));
      return null;
    }
    return markUp;
  }

  private String getMarkUpForReconciliation(final byte[] pdf) throws IOException {
    try (PDDocument doc = PDDocument.load(pdf)) {
      final GetCharLocationAndSize stripper = new GetCharLocationAndSize();
      stripper.setSortByPosition(true);
      stripper.setStartPage(0);
      stripper.setEndPage(doc.getNumberOfPages());
      final Writer dummy = new OutputStreamWriter(new ByteArrayOutputStream());
      stripper.writeText(doc, dummy);
      if (stripper.getFieldWrapper() == null) {
        throw new IllegalStateException("pdf file do not contain anchor symbols");
      }
      return mapper.writeValueAsString(stripper.getFieldWrapper());
    }
  }

  private Response convert(feign.Response response) {
    try {
      return mapper.readValue(response.body().asInputStream(), Response.class);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private int getPdfPagesCount(byte[] pdf, String initiatorSlackId) {
    try (PDDocument doc = PDDocument.load(pdf)) {
      doc.getPage(0);
      return doc.getNumberOfPages();
    } catch (IOException e) {
      throw new SbExtractsException("PDF doc is not readable", e, initiatorSlackId);
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

  @Getter
  public static class GetCharLocationAndSize extends PDFTextStripper {

    private FieldWrapper fieldWrapper;

    public GetCharLocationAndSize() throws IOException {
    }

    @Override
    protected void writeString(String input, List<TextPosition> textPositions) {
      if (StringUtils.equalsIgnoreCase(StringUtils.trim(input), "%$@!")) {
        TextPosition text = textPositions.get(3);
        double top = ((text.getPageHeight() - text.getEndY() - (((text.getPageHeight() * 3.5) / 100))) * 100)
            /text.getPageHeight();
        double left = (text.getEndX() * 100) / text.getPageWidth();
        fieldWrapper = new FieldWrapper(FieldWrapper.FieldForSignature.builder()
            .page(FieldWrapper.Page.builder()
                .num(getCurrentPageNo())
                .rect(FieldWrapper.Rect.builder()
                    .x(123.1)
                    .y(123.1)
                    .width(123.1)
                    .height(123.1)
                    .top(123.1)
                    .right(123.1)
                    .bottom(123.1)
                    .left(123.1)
                    .build())
                .build())
            .percentages(FieldWrapper.Percentages.builder()
                .top(top + "%")
                .left(left + "%")
                .build())
            .signer(new FieldWrapper.Signer())
            .build());
      }
    }
  }
}
