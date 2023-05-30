package com.am.sbextracts.service.integration;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;

import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.isActorReconciliationAndDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDebtorsPushService implements Process {

    private static final int DEFAULT_DELAY = 2;
    private final BambooHrSignedFileClient bambooHrSignedFile;
    private final HeaderService headerService;
    private final ReportService reportService;
    private final ResponderService slackResponderService;

    @Override
    @SbExceptionHandler
    public void process(InternalSlackEventResponse slackEventResponse) {

        com.slack.api.methods.response.chat.ChatPostMessageResponse initialMessage =
                slackResponderService.sendMessageToInitiator(slackEventResponse.getInitiatorUserId(),
                        getPostMessage("Starting....", List.of(SectionBlock.builder()
                                .text(MarkdownTextObject.builder()
                                        .text("Starting...").build()).build())));
        BambooHrSignedFileClient.SignedDocument rootDocument;
        try {
            rootDocument = bambooHrSignedFile.getSignedDocuments(headerService.getBchHeaders(slackEventResponse.getSessionId(),
                            slackEventResponse.getInitiatorUserId()));
        } catch (RetryableException | IllegalArgumentException ex) {
            throw new SbExtractsException(ex.getMessage(), ex, slackEventResponse.getInitiatorUserId());
        }
       // TagNode tagNode = getTagNode(response.body());

        String text = "Processing..";
        slackResponderService.updateMessage(
                initialMessage, text, slackEventResponse.getInitiatorUserId());

        Map<String, String> employeesEmails = reportService.getEmployeesEmails(slackEventResponse.getInitiatorUserId());
        text = text + "..";
        slackResponderService.updateMessage(
                initialMessage, text, slackEventResponse.getInitiatorUserId());

//        Set<String> notSignedFilesInn = Arrays
//                .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName",
//                        true, false))
//                .filter(td -> ParsingUtils.isActorReconciliationAndDate(td, slackEventResponse.getDate()))
//                .filter(rec -> !ParsingUtils.isSigned(rec))
//                .map(ParsingUtils::getName)
//                .map(ParsingUtils::getInn)
//                .collect(Collectors.toSet());

        Set<String> notSignedFilesInn = rootDocument.getDocuments().stream()
            .filter(Objects::nonNull)
            .filter(doc -> isActorReconciliationAndDate(doc, slackEventResponse.getDate()))
            .filter(Predicate.not(ParsingUtils::isSigned))
            .map(BambooHrSignedFileClient.Document::getName)
            .map(ParsingUtils::getInn)
            .collect(Collectors.toSet());

        log.info("Count of user for notification {}", notSignedFilesInn.size());
        slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                "Count of user for notification" + notSignedFilesInn.size());
        for (String inn : notSignedFilesInn) {
            try {
                TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
            } catch (InterruptedException e) {
                log.error("sleep was interrupted", e);
                Thread.currentThread().interrupt();
            }
            String userEmail = employeesEmails.get(inn);
            try {
                String conversationIdWithUser = slackResponderService.getConversationIdByEmail(userEmail,
                        slackEventResponse.getInitiatorUserId());
                slackResponderService.sendMessage(
                        getPostMessage("Unsigned akt", List.of(SectionBlock.builder()
                                .text(MarkdownTextObject.builder()
                                        .text(
                                                ":alert:\n" +
                                                        "Hi, Please take a moment to sign Acts of acceptance with coworking. \n" +
                                                        "If you have any questions regarding the documents," +
                                                        " you can contact Marina Stankevich via slack or email").build()).build()))
                                .channel(conversationIdWithUser)
                                .build(), userEmail, slackEventResponse.getInitiatorUserId());
                slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                        String.format("User: %s received a notification", userEmail));
            } catch (Exception ex) {
                log.error("Error during debtor push for {} and inn {}", userEmail, inn, ex);
                slackResponderService.log(slackEventResponse.getInitiatorUserId(),
                        String.format("Error for inn %s: %s ", inn, ex.getMessage()));
            }
        }

        log.info("DONE");
        slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Done");
    }

    private ChatPostMessageRequest.ChatPostMessageRequestBuilder getPostMessage(String headerText,
                                                                                List<LayoutBlock> layoutBlocks) {
        return ChatPostMessageRequest.builder()
                .text(headerText)
                .blocks(layoutBlocks);
    }

}
