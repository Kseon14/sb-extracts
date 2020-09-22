package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.vo.BMessage;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.models.blocks.Divider;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

@Component
public class BMessageListener implements ApplicationListener<BMessage> {

    private final ResponderService slackResponderService;

    @Autowired
    public BMessageListener(ResponderService slackResponderService) {
        this.slackResponderService = slackResponderService;
    }

    @Override
    public void onApplicationEvent(BMessage message) {

        String conversationIdWithUser = slackResponderService.getConversationIdByEmail(message.getUserEmail(),
                message.getAuthorSlackId());

        slackResponderService.sendMessage(
                ChatPostMessageParams.builder()
                        .setText("Потрiбна инфа з твого боку...")
                        .setChannelId(conversationIdWithUser)
                        .addBlocks(Section.of(
                                Text.of(TextType.MARKDOWN, String.format(":wave: Привіт, %s!\n"
                                                + "%s \n"
                                                + ":date: Будь ласка, зроби це раніше *%s* \n"
                                                + "У випадку виникнення питань, зверніться до <@%s> :paw_prints:",
                                        message.getFullName(),
                                        message.getText(),
                                        new SimpleDateFormat("dd MMM").format(message.getDueDate()),
                                        message.getAuthorSlackId()))),
                                Divider.builder().build()
                        ).build(), message.getUserEmail(), message.getAuthorSlackId());

        slackResponderService.sendCompletionMessage(message.getAuthorSlackId(), message.getFullName(), message.getUserEmail());

    }
}
