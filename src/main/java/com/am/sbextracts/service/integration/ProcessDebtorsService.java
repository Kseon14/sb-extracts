package com.am.sbextracts.service.integration;

import com.am.sbextracts.client.BambooHrSignClient;
import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.am.sbextracts.model.Folder;
import com.am.sbextracts.model.InternalSlackEventResponse;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.utils.ParsingUtils;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.methods.params.chat.ChatUpdateMessageParams;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import com.hubspot.slack.client.models.response.chat.ChatPostMessageResponse;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Collectors;

import static com.am.sbextracts.service.integration.utils.ParsingUtils.getTagNode;
import static com.am.sbextracts.service.integration.utils.ParsingUtils.isAkt;
import static com.am.sbextracts.service.integration.utils.ParsingUtils.isRequiredTag;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDebtorsService implements Process {

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

        Map<String, String> employees = reportService.getEmployees();
        text = text + "..";
        slackResponderService.updateMessage(
                initialMessage, text, slackEventResponse.getInitiatorUserId());

        Set<String> notSignedFiles = Arrays
                .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName",
                        true, false))
                .filter(td -> ParsingUtils.isAktAndDate(td, slackEventResponse.getAktDate()))
                .filter(rec -> !ParsingUtils.isSigned(rec))
                .map(ParsingUtils::getName)
                .collect(Collectors.toSet());

        Set<String> filesSentForSignature = Arrays
                .stream(tagNode.getElementsByAttValue("class", "fab-Table__cell ReportsTable__reportName",
                        true, false))
                .filter(td -> ParsingUtils.isAktAndDate(td, slackEventResponse.getAktDate()))
                .map(ParsingUtils::getName)
                .collect(Collectors.toSet());

        var offset = 0;
        int fileCount;
        List<String> notSentFiles = new ArrayList<>();
        do {
            Folder folder = getFolderContent(slackEventResponse.getSessionId(), offset,
                    slackEventResponse.getBambooFolderId(), slackEventResponse.getInitiatorUserId());

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
                ChatUpdateMessageParams.builder()
                        .setText("Debtors...")
                        .setTs(initialMessage.getTs())
                        .setChannelId(initialMessage.getChannel())
                        .addBlocks(Section.of(
                                Text.of(TextType.MARKDOWN, "*Not Signed (" + notSignedFiles.size() + ")*\n")))
                , slackEventResponse.getInitiatorUserId());

        if (notSentFiles.size() != 0) {
            slackResponderService.sendMessageToInitiator(slackEventResponse.getInitiatorUserId(),
                    ChatPostMessageParams.builder()
                            .setText("Not Sent")
                            .addBlocks(Section.of(
                                    Text.of(TextType.MARKDOWN, "*Not Sent (" + notSentFiles.size() + ")*\n")))
            );
            int currentPosition = 0;
            do {
                int newPosition = currentPosition + 40;
                slackResponderService.sendMessageToInitiator(slackEventResponse.getInitiatorUserId(),
                        ChatPostMessageParams.builder()
                                .setText("Not Sent")
                                .addBlocks(Section.of(
                                        Text.of(TextType.MARKDOWN,
                                                String.join("\n", notSentFiles.subList(currentPosition,
                                                        Math.min(notSentFiles.size(), newPosition))))))
                );
                currentPosition = newPosition;
            } while (notSentFiles.size() > currentPosition);
        }
        log.info("DONE");
        slackResponderService.log(slackEventResponse.getInitiatorUserId(), "Done");
    }

    public Folder getFolderContent(String sessionId, int offset, int sectionId, String initiatorSlackId) {
        return bambooHrSignClient.getFolderContent(headerService.getBchHeaders(sessionId, initiatorSlackId),
                BambooHrSignClient.FolderParams.of(sectionId, offset));
    }

}
