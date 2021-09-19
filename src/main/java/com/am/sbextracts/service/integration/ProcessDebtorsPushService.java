package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrSignClient;
import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.am.sbextracts.model.Folder;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import com.hubspot.slack.client.models.response.chat.ChatPostMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.htmlcleaner.TagNode;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.getTagNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDebtorsPushService implements Process {

    private final BambooHrSignedFileClient bambooHrSignedFile;
    private final BambooHrSignClient bambooHrSignClient;
    private final HeaderService headerService;
    private final ReportService reportService;
    private final ResponderService slackResponderService;


    @Override
    public void process(InternalSlackEventResponse slackEventResponse) {

        ChatPostMessageResponse initialMessage = slackResponderService.sendMessageToInitiator(
                slackEventResponse.getInitiatorUserId(),
                ChatPostMessageParams.builder()
                        .setText("Starting....")
                        .addBlocks(Section.of(
                                Text.of(TextType.MARKDOWN, "Starting...")))
        );
        feign.Response response = bambooHrSignedFile
                .getSignedDocumentList(headerService.getBchHeaders(slackEventResponse.getSessionId(),
                        slackEventResponse.getInitiatorUserId()));
        TagNode tagNode = getTagNode(response.body());

        String text = "Processing..";
        slackResponderService.updateMessage(
                initialMessage, text, slackEventResponse.getInitiatorUserId());

        Map<String, String> employeesEmails = reportService.getEmployeesEmails();
        text = text + "..";
        slackResponderService.updateMessage(
                initialMessage, text, slackEventResponse.getInitiatorUserId());

        Set<String> notSignedFilesInn = Arrays
                .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName",
                        true, false))
                .filter(td -> ParsingUtils.isAktAndDate(td, slackEventResponse.getDate()))
                .filter(rec -> !ParsingUtils.isSigned(rec))
                .map(ParsingUtils::getName)
                .map(ParsingUtils::getInn)
                .collect(Collectors.toSet());

        log.info("Count of user for notification {}", notSignedFilesInn.size());
        for (String inn : notSignedFilesInn) {
            String userEmail = employeesEmails.get(inn);
            try {
                String conversationIdWithUser = slackResponderService.getConversationIdByEmail(userEmail,
                        slackEventResponse.getInitiatorUserId());
                slackResponderService.sendMessage(
                        ChatPostMessageParams.builder()
                                .setText("Unsigned akt")
                                .setChannelId(conversationIdWithUser)
                                .addBlocks(Section.of(
                                        Text.of(TextType.MARKDOWN, String.format(
                                                ":alert:\n" +
                                                        "Hi, you have unsigned akt from TechHosting from %s, please sign it in bambooHr\n" +
                                                        "If you have any questions, please contact <@%s> :paw_prints:",
                                                slackEventResponse.getDate(), slackEventResponse.getInitiatorUserId())))
                                ).build(), userEmail, slackEventResponse.getInitiatorUserId());
                slackResponderService.log(slackEventResponse.getInitiatorUserId(), String.format("User: %s received a notification", userEmail));
            } catch (Exception ex) {
                log.error("Error during debtor push", ex);
                slackResponderService.log(slackEventResponse.getInitiatorUserId(), String.format("Error: %s", ex.getMessage()));
            }
        }

        log.info("DONE");
        slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Done");
    }

    public Folder getFolderContent(String sessionId, int offset, int sectionId, String initiatorSlackId) {
        return bambooHrSignClient.getFolderContent(headerService.getBchHeaders(sessionId, initiatorSlackId),
                BambooHrSignClient.FolderParams.of(sectionId, offset));
    }

}
