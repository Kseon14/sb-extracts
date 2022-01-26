package com.am.sbextracts.service.integration;

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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.getTagNode;
import static com.am.sbextracts.service.integration.utils.ParsingUtils.isAkt;
import static com.am.sbextracts.service.integration.utils.ParsingUtils.isRequiredTag;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDebtorsService implements Process {

    private final static int DEFAULT_DELAY = 1;

    private final BambooHrSignedFileClient bambooHrSignedFile;
    private final HeaderService headerService;
    private final ReportService reportService;
    private final ResponderService slackResponderService;
    private final SignClientCommon signClientCommon;

    @Override
    @SbExceptionHandler
    @SneakyThrows
    public void process(InternalSlackEventResponse slackEventResponse) {

        ChatPostMessageResponse initialMessage = slackResponderService.sendMessageToInitiator(
                slackEventResponse.getInitiatorUserId(),
                ChatPostMessageRequest.builder()
                        .text("Starting....")
                        .blocks(List.of(SectionBlock.builder()
                                .text(MarkdownTextObject.builder()
                                        .text("Starting...").build()).build())));
        feign.Response response;
        Map<String, String> bchHeaders;
        try {
            bchHeaders = headerService.getBchHeaders(slackEventResponse.getSessionId(),
                    slackEventResponse.getInitiatorUserId());
        } catch (IllegalArgumentException ex) {
            throw new SbExtractsException(ex.getMessage(), slackEventResponse.getInitiatorUserId());
        }
        try {
            response = bambooHrSignedFile.getSignedDocumentList(bchHeaders);
        } catch (RetryableException ex) {
            throw new SbExtractsException(ex.getMessage(), ex, slackEventResponse.getInitiatorUserId());
        }
        TagNode tagNode = getTagNode(response.body());

        String text = "Processing..";
        slackResponderService.updateMessage(
                initialMessage, text, slackEventResponse.getInitiatorUserId());

        Map<String, String> employees = reportService.getEmployees();
        text = text + "..";
        slackResponderService.updateMessage(
                initialMessage, text, slackEventResponse.getInitiatorUserId());

        Set<String> notSignedFiles = Arrays
                .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName",
                        true, false))
                .filter(td -> ParsingUtils.isAktAndDate(td, slackEventResponse.getDate()))
                .filter(rec -> !ParsingUtils.isSigned(rec))
                .map(ParsingUtils::getName)
                .collect(Collectors.toSet());

        Set<String> filesSentForSignature = Arrays
                .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName",
                        true, false))
                .filter(td -> ParsingUtils.isAktAndDate(td, slackEventResponse.getDate()))
                .map(ParsingUtils::getName)
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
                    .filter(isAkt)
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
                                .text(MarkdownTextObject.builder().text("*Not Signed (" + notSignedFiles.size() + ")*\n").build()).build())),
                slackEventResponse.getInitiatorUserId());

        if (notSignedFiles.size() > 0) {
            int currentPosition = 0;
            do {
                int newPosition = currentPosition + 40;
                slackResponderService.sendMessageToInitiator(slackEventResponse.getInitiatorUserId(),
                        ChatPostMessageRequest.builder()
                                .text("Not Signed")
                                .blocks(List.of(SectionBlock.builder()
                                        .text(MarkdownTextObject.builder()
                                                .text(String.join("\n", new ArrayList<>(notSignedFiles).subList(currentPosition,
                                                        Math.min(notSignedFiles.size(), newPosition)))).build()).build())));
                currentPosition = newPosition;
                TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
            } while (notSignedFiles.size() > currentPosition);
        }

        slackResponderService.sendMessageToInitiator(slackEventResponse.getInitiatorUserId(),
                ChatPostMessageRequest.builder()
                        .text("Not Sent")
                        .blocks(List.of(SectionBlock.builder()
                                .text(MarkdownTextObject.builder()
                                        .text("*Not Sent (" + notSentFiles.size() + ")*\n").build()).build())));
        if (notSentFiles.size() > 0) {
            int currentPosition = 0;
            do {
                int newPosition = currentPosition + 40;
                slackResponderService.sendMessageToInitiator(slackEventResponse.getInitiatorUserId(),
                        ChatPostMessageRequest.builder()
                                .text("Not Sent")
                                .blocks(List.of(SectionBlock.builder()
                                        .text(MarkdownTextObject.builder()
                                                .text(String.join("\n", notSentFiles.subList(currentPosition,
                                                        Math.min(notSentFiles.size(), newPosition)))).build()).build())));
                currentPosition = newPosition;
                TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
            } while (notSentFiles.size() > currentPosition);
        }
        log.info("DONE");
        slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Done");
    }

}
