package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrSignClient;
import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.htmlcleaner.TagNode;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarkUpAndSignatureService {

    private final static int ATTEMPTS_LIMIT = 8;
    private final static int ATTEMPTS_DELAY = 4;
    private final static int DEFAULT_DELAY = 2;
    private final static String PROPERTIES_FILE = "signature-markup.properties";
    private final static String PROCESSED_ID_FILE_NAME = "processedId.log";
    private final static String PROCESSED_ID_FILE_NAME_PREFIX = "markup";

    private final static Properties prop = new Properties();

    private final ObjectMapper mapper;
    private final ReportService reportService;
    private final BambooHrSignClient bambooHrSignClient;
    private final HeaderService headerService;;
    private final BambooHrSignedFileClient bambooHrSignedFile;

    // already signed documents
    private static final Predicate<TagNode> isNotSigned = (tagNode) -> tagNode.getParent().getParent()
            .getAttributeByName("data-is-esig").equals("");

    private static final Predicate<TagNode> isRequiredTag = (tagNode) -> tagNode.getAttributes().containsKey("onclick")
            && tagNode.getAttributeByName("onclick").startsWith("previewFile");


    // get fist 100 documents for specific folder
    // parse and get all docIds
    // add signature markup
    // get the signature ID
    // get from doc name INN
    // send to signature for INN mapping
//
//    @SneakyThrows
//    public void signDocuments(MarkupDto markupDto) {
//        Map<String, String> employees = reportService.getEmployees();
//        int fileCount;
//        var offset = 0;
//        var processed = 0;
//
//        final List<String> processedIds = new ArrayList<>();
//        File file = new File(String.format("%s-%s-%s", markupDto.getDate(), PROCESSED_ID_FILE_NAME_PREFIX,
//                PROCESSED_ID_FILE_NAME));
//        if (file.exists()) {
//            processedIds.addAll(Files.readAllLines(Paths.get(file.getPath())));
//        }
//        do {
//            Folder folder = getFolderContent(markupDto.getSessionId(), offset, markupDto.getSectionId());
//            log.info("start processing folder: {}, filesCount: {}, offset: {}", folder.getSectionName(),
//                    folder.getSectionFileCount(), offset);
//            TagNode tagNode = new HtmlCleaner().clean(folder.getHtml());
//
//            List<DocumentInfo> infos = tagNode.getElementListByName("button", true).stream().filter(isRequiredTag)
//                    //.filter(isNotSigned)
//                    .filter(isAkt)
//                    .map(b -> DocumentInfo.of(ParsingUtils.getInn(b),
//                            ParsingUtils.getFileId(b), ParsingUtils.getTemplateFileId(b)))
//                    .filter(info -> !CollectionUtils.containsAny(processedIds, info.getFileId()))
//                    .collect(Collectors.toList());
//            log.info("Following Documents count {} will be processed", infos.size());
//
//            for (var info : infos) {
//                String employeeInternalId = employees.get(info.getInn());
//                if (employeeInternalId == null) {
//                    log.error("user with inn: {} not found", info.getInn());
//                    continue;
//                }
//                log.info("Start processing {}....", info.getInn());
//                processDocuments(employeeInternalId, info, markupDto.getSessionId(), file);
//                processed++;
//                if (processed > markupDto.getTotalToProcessing()) {
//                    log.info("Processed {}", processed);
//                    break;
//                }
//            }
//            offset = folder.getOffset();
//            fileCount = folder.getSectionFileCount();
//        } while (offset < fileCount);
//        log.info("DONE");
//    }
//
//    private Folder getFolderContent(String sessionId, int offset, int sectionId) {
//        return bambooHrSignClient.getFolderContent(headerService.getBchHeaders(sessionId),
//                BambooHrSignClient.FolderParams.of(sectionId, offset));
//    }
//
//    @SneakyThrows
//    private void processDocuments(String employeeInternalId, DocumentInfo info, String sessionId, File file) {
//        Response updateTemplateResponse = convert(
//                bambooHrSignClient.createTemplate(headerService.getBchHeaders(sessionId),
//                        new BambooHrSignClient.CrtRequest(true, info.getTemplateFileId())));
//        log.info("updateTemplateResponse {}", updateTemplateResponse.isSuccess());
//        if (updateTemplateResponse.isSuccess()) {
//            Response isReady;
//            int attempts = 0;
//            do {
//                isReady = bambooHrSignClient.isReady(headerService.getBchHeaders(sessionId),
//                        BambooHrSignClient.CompleteParams.of(info.getFileId()));
//                TimeUnit.SECONDS.sleep(ATTEMPTS_DELAY);
//                attempts++;
//            } while (!isReady.isSuccess() && attempts <= ATTEMPTS_LIMIT);
//
//            log.info("isReady {}", isReady.isSuccess());
//            if (isReady.isSuccess()) {
//                TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
//                Response completed = bambooHrSignClient.getCompleted(headerService.getBchHeaders(sessionId),
//                        BambooHrSignClient.CompleteParams.of(info.getFileId()));
//                log.info("updateTemplateResponse {}", updateTemplateResponse.isSuccess());
//                if (completed.isSuccess()) {
//                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
//                    int pagesCount = getPdfPagesCount(sessionId, info.getTemplateFileId());
//                    if (pagesCount == 0) {
//                        log.error("File download problem for inn {}", info.getInn());
//                        return;
//                    }
//                    String markUp = getMarkUp(pagesCount);
//                    if (StringUtils.isBlank(markUp)) {
//                        log.error("markup source file read problem");
//                        return;
//                    }
//                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
//                    Response update = convert(bambooHrSignClient.updateTemplate(headerService.getBchHeaders(sessionId),
//                            new BambooHrSignClient.UpdateRequest(true,
//                                    Integer.parseInt(completed.getEsignatureTemplateId()), markUp)));
//                    log.info("Response {}, error {}, message {}", update.isSuccess(), update.getError(),
//                            update.getMessage());
//
//                    Map<String, Object> signParams = Map.of("allIds", "off", "message",
//                            "Please take a moment to sign this document", "employeeIds[]", employeeInternalId);
//                    // Map<String, Object> signParams = new HashMap<>();
//                    // signParams.put("allIds", "off");
//                    // signParams.put("message", "Please take a moment to sign this document");
//                    // signParams.put("employeeIds[]", employeeInternalId);
//                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
//                    Response signatureRequest = convert(bambooHrSignClient.signatureRequest(
//                            headerService.getBchHeaders(sessionId), signParams, update.getWorkflowId()));
//                    log.info("Document for inn: {}, error:{}, success: {}", info.getInn(), signatureRequest.getError(),
//                            signatureRequest.isSuccess());
//                    FileUtils.writeStringToFile(file, info.getFileId() + "\r\n", StandardCharsets.UTF_8.toString(), true);
//
//                }
//            }
//        }
//    }



//    @SneakyThrows
//    private Response convert(feign.Response response) {
//        return mapper.readValue(response.body().asInputStream(), Response.class);
//    }
//
//    @SneakyThrows
//    private int getPdfPagesCount(String sessionId, int templateFileId) {
//        byte[] pdf = bambooHrSignClient.getPdf(headerService.getBchHeaders(sessionId), templateFileId);
//        if (pdf.length == 0) {
//            return 0;
//        }
//        try (PDDocument doc = PDDocument.load(pdf)) {
//            doc.getPage(0);
//            return doc.getNumberOfPages();
//        }
//    }
//
//    private String getMarkUp(int pagesCount) throws IOException {
//        if (prop.isEmpty()) {
//            try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
//                if (input == null) {
//                    throw new FileNotFoundException("Resource not found: " + PROPERTIES_FILE);
//                }
//                prop.load(input);
//            }
//        }
//        return prop.getProperty(pagesCount + "");
//    }
}
