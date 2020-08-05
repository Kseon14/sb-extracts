package com.am.sbextracts.service;

import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackFileInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import org.asynchttpclient.RequestBuilder;

import java.util.concurrent.ExecutionException;

public interface ResponderService {

    void sendMessage(ChatPostMessageParams params);

    String getConversationIdByEmail(String userEmail);

    String getConversationIdBySlackId(String userSlackId);

    void sendCompletionMessage(String userSlackId, String userFullName, String userEmail);

    void sendFile(String fileName, String userEmail);

    RequestBuilder getBuilder(String httpMethod);

    SlackFileInfo getFileInfo(SlackEvent.FileMetaInfo fileMetaInfo) throws ExecutionException, InterruptedException,
            JsonProcessingException;

    void downloadFile(String fileName, SlackFileInfo slackFile) throws ExecutionException, InterruptedException;

    void sendErrorMessageToInitiator(String userSlackId, String shortText, String text);
}
