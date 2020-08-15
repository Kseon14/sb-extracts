package com.am.sbextracts.service;

import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackFileInfo;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;

public interface ResponderService {

    void sendMessage(ChatPostMessageParams params, String userEmail, String initiatorSlackId);

    String getConversationIdByEmail(String userEmail, String initiatorSlackId);

    void sendCompletionMessage(String initiatorSlackId, String userFullName, String userEmail);

    void sendFile(String fileName, String userEmail, String initiatorSlackId);

    SlackFileInfo getFileInfo(SlackEvent.FileMetaInfo fileMetaInfo) throws Exception;

    void downloadFile(String fileName, SlackFileInfo slackFile);

    void sendErrorMessageToInitiator(String userSlackId, String shortText, String text);

    String getConversationIdBySlackId(String userSlackId, String initiatorSlackId);

    }
