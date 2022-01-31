package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.vo.BMessage;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BMessageListener implements ApplicationListener<BMessage> {

    private final ResponderService slackResponderService;

    @Override
    public void onApplicationEvent(BMessage message) {

        String conversationIdWithUser = slackResponderService.getConversationIdByEmail(message.getUserEmail(),
                message.getAuthorSlackId());

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
                                                message.getAuthorSlackId(),
                                                new SimpleDateFormat("dd MMM").format(message.getDueDate()))).build()).build())
                        ).build(), message.getUserEmail(), message.getAuthorSlackId());

        slackResponderService.sendMessageToInitiator(message.getAuthorSlackId(), message.getFullName(), message.getUserEmail());

    }
}
