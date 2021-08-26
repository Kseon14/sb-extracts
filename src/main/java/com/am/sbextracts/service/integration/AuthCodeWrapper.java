package com.am.sbextracts.service.integration;

import com.am.sbextracts.service.ResponderService;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.util.Preconditions;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class AuthCodeWrapper extends AuthorizationCodeInstalledApp {

    private final ResponderService slackResponderService;
    private final String initiatorSlackId;
    /**
     * @param flow     authorization code flow
     * @param receiver verification code receiver
     */
    public AuthCodeWrapper(AuthorizationCodeFlow flow, VerificationCodeReceiver receiver,
                           ResponderService slackResponderService, String initiatorSlackId) {
        super(flow, receiver);
        this.slackResponderService = slackResponderService;
        this.initiatorSlackId = initiatorSlackId;
    }

    @Override
    protected void onAuthorization(AuthorizationCodeRequestUrl authorizationUrl) throws IOException {
        browse(authorizationUrl.build(), slackResponderService, initiatorSlackId);
    }

    public static void browse(String url, ResponderService slackResponderService, String initiatorSlackId) {
        Preconditions.checkNotNull(url);
        // Ask user to open in their browser using copy-paste
        slackResponderService.sendMessageToInitiator(
                initiatorSlackId,
                ChatPostMessageParams.builder()
                        .setText("url")
                        .addBlocks(Section.of(
                                Text.of(TextType.MARKDOWN, url)))
        );
    }
}
