package com.am.sbextracts.service.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.springframework.stereotype.Service;

import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.Folder;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatUpdateRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;

import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.FILTER_BY_DATE_AND_DOCUMENT_TYPE;
import static com.am.sbextracts.service.integration.utils.ParsingUtils.isSpecificDateAndDocumentType;
import static com.am.sbextracts.service.integration.utils.ParsingUtils.isRequiredTag;

@Slf4j
@Service
@RequiredArgsConstructor
@Deprecated
public class ProcessDebtorsService implements Process {

    private static final int DEFAULT_DELAY = 1;

    private final BambooHrSignedFileClient bambooHrSignedFile;
    private final HeaderService headerService;
    private final ReportService reportService;
    private final ResponderService slackResponderService;
    private final SignClientCommon signClientCommon;

    @Override
    @SbExceptionHandler
    public void process(InternalSlackEventResponse slackEventResponse) {

        ChatPostMessageResponse initialMessage = slackResponderService.sendMessageToInitiator(
                slackEventResponse.getInitiatorUserId(),
                getPostMessage("Starting....", "Starting..."));
        log.debug("Starting...");
        BambooHrSignedFileClient.SignedDocument rootDocument;
        Map<String, String> bchHeaders;
        try {
            bchHeaders = headerService.getBchHeaders(slackEventResponse.getSessionId(),
                    slackEventResponse.getInitiatorUserId());
        } catch (IllegalArgumentException ex) {
            throw new SbExtractsException(ex.getMessage(), slackEventResponse.getInitiatorUserId());
        }
        try {
            rootDocument = bambooHrSignedFile.getSignedDocuments(bchHeaders);
        } catch (RetryableException ex) {
            throw new SbExtractsException(ex.getMessage(), ex, slackEventResponse.getInitiatorUserId());
        }
        String text = "Processing..";
        slackResponderService.updateMessage(
                initialMessage, text, slackEventResponse.getInitiatorUserId());

        Map<String, String> employees = reportService.getEmployees();
        text = text + "..";
        slackResponderService.updateMessage(
                initialMessage, text, slackEventResponse.getInitiatorUserId());
        log.debug("Start parsing documents");
       // TagNode tagNode = getTagNode(response.body());
//        Set<String> notSignedFiles = Arrays
//            .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName",
//                true, false))
//            .filter(td -> ParsingUtils.isActorReconciliationAndDate(td, slackEventResponse.getDate()))
//            .filter(rec -> !ParsingUtils.isSigned(rec))
//            .map(ParsingUtils::getName)
//            .collect(Collectors.toSet());

        Set<String> notSignedFiles = rootDocument.getDocuments().stream()
            .filter(Objects::nonNull)
            .filter(doc -> isSpecificDateAndDocumentType(doc, slackEventResponse))
            .filter(Predicate.not(ParsingUtils::isSigned))
            .map(BambooHrSignedFileClient.Document::getName)
            .collect(Collectors.toSet());

        Set<String> filesSentForSignature = rootDocument.getDocuments().stream()
            .filter(Objects::nonNull)
            .filter(doc -> isSpecificDateAndDocumentType(doc, slackEventResponse))
            .map(BambooHrSignedFileClient.Document::getName)
            .collect(Collectors.toSet());

        var offset = 0;
        int fileCount;
        List<String> notSentFiles = new ArrayList<>();
        do {
            Folder folder = signClientCommon.getFolderContent(offset, slackEventResponse.getFolderId(), bchHeaders);

            text = text + "..";
            slackResponderService.updateMessage(
                    initialMessage, text, slackEventResponse.getInitiatorUserId());

            TagNode tagNodeFolderWithActs = new HtmlCleaner().clean(folder.getHtml());
            notSentFiles.addAll(tagNodeFolderWithActs.getElementListByName("button", true).stream()
                    .filter(isRequiredTag)
                    .filter(tag -> FILTER_BY_DATE_AND_DOCUMENT_TYPE.test(tag, slackEventResponse))
                    .filter(b -> employees.get(ParsingUtils.getInn(b)) != null)
                    .map(ParsingUtils::getFileTitle)
                    .filter(name -> !CollectionUtils.containsAny(filesSentForSignature, name))
                    .collect(Collectors.toSet()));
            offset = folder.getOffset();
            fileCount = folder.getSectionFileCount();
        } while (offset < fileCount);

        slackResponderService.updateMessage(
                ChatUpdateRequest.builder()
                        .text("Debtors...")
                        .ts(initialMessage.getTs())
                        .channel(initialMessage.getChannel())
                        .blocks(List.of(SectionBlock.builder()
                                .text(MarkdownTextObject.builder()
                                        .text("*Not Signed (" + notSignedFiles.size() + ")*\n").build()).build())),
                slackEventResponse.getInitiatorUserId());

        if (!notSignedFiles.isEmpty()) {
            int currentPosition = 0;
            do {
                int newPosition = currentPosition + 40;
                slackResponderService.sendMessageToInitiator(slackEventResponse.getInitiatorUserId(),
                        getPostMessage("Not Signed", String.join("\n",
                                new ArrayList<>(notSignedFiles).subList(currentPosition,
                                        Math.min(notSignedFiles.size(), newPosition)))));
                currentPosition = newPosition;
                try {
                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
                } catch (InterruptedException e) {
                    log.error("sleep was interrupted", e);
                    Thread.currentThread().interrupt();
                }
            } while (notSignedFiles.size() > currentPosition);
        }

        slackResponderService.sendMessageToInitiator(slackEventResponse.getInitiatorUserId(),
                getPostMessage("Not Sent", "*Not Sent (" + notSentFiles.size() + ")*\n"));
        if (!notSentFiles.isEmpty()) {
            int currentPosition = 0;
            do {
                int newPosition = currentPosition + 40;
                slackResponderService.sendMessageToInitiator(slackEventResponse.getInitiatorUserId(),
                        getPostMessage("Not Sent", String.join("\n", notSentFiles.subList(currentPosition,
                                Math.min(notSentFiles.size(), newPosition)))));
                currentPosition = newPosition;
                try {
                    TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
                } catch (InterruptedException e) {
                    log.error("sleep was interrupted", e);
                    Thread.currentThread().interrupt();
                }
            } while (notSentFiles.size() > currentPosition);
        }
        log.info("DONE");
        slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Done");
    }

    public static ChatPostMessageRequest.ChatPostMessageRequestBuilder getPostMessage(String headerText, String text) {
        return ChatPostMessageRequest.builder()
                .text(headerText)
                .blocks(List.of(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text(text).build()).build()));
    }

}
