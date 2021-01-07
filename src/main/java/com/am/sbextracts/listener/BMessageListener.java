package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.vo.BMessage;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

@Component
@RequiredArgsConstructor
public class BMessageListener implements ApplicationListener<BMessage> {

    private final ResponderService slackResponderService;

    @Override
    public void onApplicationEvent(BMessage message) {

        String conversationIdWithUser = slackResponderService.getConversationIdByEmail(message.getUserEmail(),
                message.getAuthorSlackId());

        slackResponderService.sendMessage(
                ChatPostMessageParams.builder()
                        .setText("Потрiбна инфа з твого боку...")
                        .setChannelId(conversationIdWithUser)
                        .addBlocks(Section.of(
                                Text.of(TextType.MARKDOWN, String.format("Привіт, %s!\n"
                                                + "%s \n"
                                                + "Це все потрібно надіслати :point_right: <@%s> :paw_prints:\n"
                                                + ":date: Будь ласка, зроби це раніше *%s*",
                                        message.getFullName(),
                                        message.getText(),
                                        message.getAuthorSlackId(),
                                        new SimpleDateFormat("dd MMM").format(message.getDueDate()))))
                        ).build(), message.getUserEmail(), message.getAuthorSlackId());

        slackResponderService.sendCompletionMessage(message.getAuthorSlackId(), message.getFullName(), message.getUserEmail());

    }
}
