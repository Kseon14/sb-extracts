package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrApiClient;
import com.am.sbextracts.client.NetSuiteFileClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.FileInfo;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.TagNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingInvoiceService implements Process {
    private final static String PROCESSED_ID_FILE_NAME_PREFIX = "invoice";
    private final static String PROCESSED_ID_FILE_NAME = "processedId.log";

    @Value("${app.reportGFolderId}")
    private final String reportGFolderId;
    @Value("${app.perRequestProcessingFilesCount}")
    private final int perRequestProcessingFilesCount;

    private static final String SEPARATOR = ",";

    private final NetSuiteFileClient netSuiteFileClient;
    private final BambooHrApiClient bambooHrApiClient;
    private final HeaderService headerService;
    private final ReportService reportService;
    private final GDriveService gDriveService;
    private final ResponderService slackResponderService;

    @SbExceptionHandler
    @SneakyThrows
    @Override
    public void process(InternalSlackEventResponse slackEventResponse) {
        gDriveService.validateFolderExistence(reportGFolderId, slackEventResponse.getInitiatorUserId());
        Map<String, String> employees = reportService.getEmployees();
        File file = null;
        long dateOfModification = -1;
        String logFileName = String.format("%s-%s-%s", slackEventResponse.getDate(), PROCESSED_ID_FILE_NAME_PREFIX,
                PROCESSED_ID_FILE_NAME);
        final Map<String, String> processedIds = new HashMap<>();
        try {

            file = gDriveService.getFile(logFileName, slackEventResponse.getInitiatorUserId());

            if (file.exists()) {
                processedIds.putAll(parseLogFile(Files.readAllLines(Paths.get(file.getPath()))));
            }
            dateOfModification = file.lastModified();
            feign.Response response = netSuiteFileClient.getInvoices(HeaderService.getNsHeaders(slackEventResponse.getSessionId()),
                    NetSuiteFileClient.FolderParams.of(slackEventResponse.getFolderId()));
            TagNode tagNode = ParsingUtils.getTagNode(response.body());

            List<FileInfo> fileInfos = tagNode.getElementListByName("a", true).stream()
                    .filter(el -> StringUtils.startsWith(el.getAttributeByName("onclick"), ("previewMedia")))
                    .map(ProcessingInvoiceService::getFileInfo)
                    .filter(fileInfo -> !processedIds.containsKey(fileInfo.getId()))
                    .filter(fileInfo -> getEmployeeId(fileInfo, employees) != null)
                    .collect(Collectors.toList());
            log.info("Documents for download: {}", fileInfos.size());
            slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                    String.format("Documents for download: %s", fileInfos.size()));

            for (int i = 0; i < Math.min(perRequestProcessingFilesCount, fileInfos.size()); i++) {
                FileInfo fileInfo = fileInfos.get(i);
                byte[] pdf = netSuiteFileClient.getPdf(HeaderService.getNsHeaders(slackEventResponse.getSessionId()),
                        getAttributes(fileInfo.getHref()));

                try {
                    bambooHrApiClient.uploadFile(headerService.getHeaderForBchApi(), getEmployeeId(fileInfo, employees),
                            Map.of("file", prepareFile(pdf, fileInfo.getFileName()), "fileName", fileInfo.getFileName(),
                                    "share", "yes", "category", 16));
                } catch (FeignException.Forbidden ex) {
                    log.error("Do not have permission for upload:{}", fileInfo.getFileName(), ex);
                    slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                            String.format("Do not have permission for upload: %s", fileInfo.getFileName()));
                    continue;
                }
                log.info("Document uploaded [{}/{}]: {}", i + 1, Math.min(perRequestProcessingFilesCount, fileInfos.size()), fileInfo.getFileName());
                slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                        String.format("Document uploaded [%s/%s]: %s", i + 1, Math.min(perRequestProcessingFilesCount, fileInfos.size()), fileInfo.getFileName()));
                FileUtils.writeStringToFile(file, String.format("%s,%s\r\n", fileInfo.getId(), fileInfo.getFileName()), StandardCharsets.UTF_8.toString(), true);
            }
        } catch (Throwable ex) {
            log.error("Error during download of invoices", ex);
            throw new SbExtractsException("Error during download of invoices", ex, slackEventResponse.getInitiatorUserId());
        } finally {
            gDriveService.saveFile(file, dateOfModification, logFileName, slackEventResponse.getInitiatorUserId(), reportGFolderId);
        }

    }

    private String getEmployeeId(FileInfo fileInfo, Map<String, String> employees) {
        String inn = fileInfo.getFileName().split("\\.")[1];
        return employees.get(inn);
    }

    private static FileInfo getFileInfo(TagNode tagNode) {
        String href = tagNode.getParent().getParent().getElementListByAttValue("class", "dottedlink", true, false)
                .stream()
                .filter(el -> CollectionUtils
                        .containsAny(
                                el.getAllChildren().stream().filter(cn -> cn instanceof ContentNode)
                                        .map(cn -> ((ContentNode) cn).getContent()).collect(Collectors.toList()),
                                "Download"))
                .findFirst().map(el -> el.getAttributeByName("href")).orElse(null);
        String id = tagNode.getAttributeByName("href").split("\\?")[0];
        return FileInfo.of(((ContentNode) tagNode.getAllChildren().get(1)).getContent(), href, id);
    }

    private static Map<String, String> getAttributes(String href) {
        Map<String, String> attr = new HashMap<>();
        Matcher m = Pattern.compile("(\\w+)=(.*?)(?=&\\w+=|$)").matcher(href);
        while (m.find()) {
            attr.put(m.group(1), m.group(2));
        }
        return attr;
    }

    @SneakyThrows
    private static MultipartFile prepareFile(byte[] pdf, String fileName) {
        FileItem fileItem = new DiskFileItemFactory().createItem("file", MediaType.APPLICATION_PDF_VALUE, true,
                fileName);
        try (InputStream input = new ByteArrayInputStream(pdf); OutputStream os = fileItem.getOutputStream()) {
            IOUtils.copy(input, os);
        }
        return new CommonsMultipartFile(fileItem);
    }

    public static Map<String, String> parseLogFile(List<String> fileContent) {
        return fileContent.stream().collect(Collectors.toMap(row -> row.split(SEPARATOR)[0], row -> row.split(SEPARATOR)[1]));
    }
}
