package com.am.sbextracts.service;

import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackFileInfo;
import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.methods.params.chat.ChatUpdateMessageParams;
import com.hubspot.slack.client.models.response.chat.ChatPostMessageResponse;

public interface ResponderService {

    ChatPostMessageResponse sendMessage(ChatPostMessageParams params, String userEmail, String initiatorSlackId);

    String getConversationIdByEmail(String userEmail, String initiatorSlackId);

    void sendMessageToInitiator(String initiatorSlackId, String userFullName, String userEmail);

    ChatPostMessageResponse sendMessageToInitiator(String initiatorSlackId, ChatPostMessageParams.Builder builder);

    ChatPostMessageResponse log(String initiatorSlackId, String text);

    void sendFile(String fileName, String userEmail, String initiatorSlackId);

    SlackFileInfo getFileInfo(SlackEvent.FileMetaInfo fileMetaInfo) throws Exception;

    void downloadFile(String fileName, SlackFileInfo slackFile);

    void sendErrorMessageToInitiator(String userSlackId, String shortText, String text);

    String getConversationIdBySlackId(String userSlackId, String initiatorSlackId);

    void sendMarkupView(SlackInteractiveEvent slackInteractiveEvent);

    void sendDebtors(SlackInteractiveEvent slackInteractiveEvent);

    void sendDownloadSigned(SlackInteractiveEvent slackInteractiveEvent);

    void sendDownloadInvoice(SlackInteractiveEvent slackInteractiveEvent);

    void updateMessage(ChatPostMessageResponse initialMessage, String text, String initiatorSlackId);

    void updateMessage(ChatUpdateMessageParams.Builder builder, String initiatorSlackId);

    ChatPostMessageResponse sendMessage(ChatPostMessageParams params, String initiatorSlackId);
}
