package com.am.sbextracts.service;

import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackFileInfo;
import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatUpdateRequest;

public interface ResponderService {

    void sendMessage(ChatPostMessageRequest params, String userEmail, String initiatorSlackId);

    String getConversationIdByEmail(String userEmail, String initiatorSlackId);

    void sendMessageToInitiator(String initiatorSlackId, String userFullName, String userEmail);

    com.slack.api.methods.response.chat.ChatPostMessageResponse sendMessageToInitiator(String initiatorSlackId, ChatPostMessageRequest.ChatPostMessageRequestBuilder builder);

    void log(String initiatorSlackId, String text);

    void sendFile(String fileName, String userEmail, String initiatorSlackId);

    SlackFileInfo getFileInfo(SlackEvent.FileMetaInfo fileMetaInfo) throws Exception;

    void downloadFile(String fileName, SlackFileInfo slackFile);

    void sendErrorMessageToInitiator(String userSlackId, String shortText, String text);

    String getConversationIdBySlackId(String userSlackId, String initiatorSlackId);

    void sendMarkupView(SlackInteractiveEvent slackInteractiveEvent);

    void sendDebtors(SlackInteractiveEvent slackInteractiveEvent);

    void sendDownloadSigned(SlackInteractiveEvent slackInteractiveEvent);

    void sendDownloadInvoice(SlackInteractiveEvent slackInteractiveEvent);

    void pushDebtors(SlackInteractiveEvent slackInteractiveEvent);

    void updateMessage(com.slack.api.methods.response.chat.ChatPostMessageResponse initialMessage, String text, String initiatorSlackId);

    void updateMessage(ChatUpdateRequest.ChatUpdateRequestBuilder builder, String initiatorSlackId);

    com.slack.api.methods.response.chat.ChatPostMessageResponse sendMessage(ChatPostMessageRequest params, String initiatorSlackId);
}
