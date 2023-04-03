package com.am.sbextracts.listener;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.GmailService;
import com.am.sbextracts.vo.BMessage;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.am.sbextracts.listener.GlobalVariables.DEFAULT_DELAY;

@Slf4j
@Component
@RequiredArgsConstructor
public class BMessageListener {

    private final ResponderService slackResponderService;
    private final GmailService gmailService;
    @Value("${app.fromMail}")
    private final String from;

    @EventListener
    @SbExceptionHandler
    public void onApplicationEvent(BMessage message) {
        try {
            TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
        } catch (InterruptedException e) {
            log.error("sleep was interrupted", e);
            Thread.currentThread().interrupt();
        }
        String authorSlackId = message.getAuthorSlackId();
        String conversationIdWithUser = slackResponderService.getConversationIdByEmail(message.getUserEmail(),
                authorSlackId);

        slackResponderService.sendMessage(
                ChatPostMessageRequest.builder()
                        .text("Потрiбна инфа з твого боку...")
                        .channel(conversationIdWithUser)
                        .blocks(List.of(SectionBlock.builder()
                                .text(MarkdownTextObject.builder()
                                        .text(String.format("Привіт, %s!\n"
                                                        + "%s \n"
                                                        + "Це все потрібно надіслати :point_right: <@%s> :paw_prints:\n"
                                                        + ":date: Будь ласка, зроби це раніше *%s*",
                                                message.getFullName(),
                                                message.getText(),
                                                authorSlackId,
                                                new SimpleDateFormat("dd MMM").format(message.getDueDate()))).build()).build())
                        ).build(), message.getUserEmail(), authorSlackId);

        slackResponderService.sendMessageToInitiator(authorSlackId, message.getFullName(), message.getUserEmail());
        sendMail(message, authorSlackId);
    }

    private void sendMail(BMessage message, String authorSlackId) {
        if (!message.isWithEmail()) {
            return;
        }
        Set<String> emails = new HashSet<>(message.getAdditionalUserEmail());
        emails.add(message.getUserEmail());
        try {
            gmailService.sendMessage(emails,
                    "Потрiбна инфа з твого боку...",
                    String.format("Привіт, %s!<br>"
                                    + "%s <br>"
                                    + "Це все потрібно надіслати  <a href = \"mailto: %s\">мені</a><br>"
                                    + "Будь ласка, зроби це раніше <b>%s</b>",
                            message.getFullName(),
                            message.getText(),
                            from,
                            new SimpleDateFormat("dd MMM").format(message.getDueDate())),
                    authorSlackId);
        } catch (Exception e) {
            throw new SbExtractsException("Email could not be sent", e, authorSlackId);
        }
    }
}
