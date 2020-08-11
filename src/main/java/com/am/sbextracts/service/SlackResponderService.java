package com.am.sbextracts.service;

import com.am.sbextracts.pool.HttpClientPool;
import com.am.sbextracts.pool.SlackClientPool;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackFileInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.slack.client.SlackClient;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.methods.params.conversations.ConversationOpenParams;
import com.hubspot.slack.client.methods.params.users.UserEmailParams;
import com.hubspot.slack.client.models.Field;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import com.hubspot.slack.client.models.response.conversations.ConversationsOpenResponse;
import com.hubspot.slack.client.models.response.users.UsersInfoResponse;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.request.body.multipart.FilePart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

@Service
public class SlackResponderService implements ResponderService {

    private final static Logger LOGGER = LoggerFactory.getLogger(SlackResponderService.class);

    private final HttpClientPool httpClientPool;
    private final SlackClientPool slackClientPool;

    @Value("${slack.token}")
    private String token;

    public SlackResponderService(HttpClientPool httpClientPool, SlackClientPool slackClientPool) {
        this.httpClientPool = httpClientPool;
        this.slackClientPool = slackClientPool;
    }

    private SlackClient getSlackClient() throws Exception {
        SlackClient slackClient = slackClientPool.borrowObject();
        LOGGER.info("Slack Client {}", slackClient.toString());
        return slackClient;
    }

    private void returnSlackClient(SlackClient client) {
        slackClientPool.returnObject(client);
    }

    private AsyncHttpClient getHttpClient() throws Exception {
        AsyncHttpClient client = httpClientPool.borrowObject();
        LOGGER.info("http Client {}", client.toString());
        return client;
    }

    private void returnHttpClientToPool(AsyncHttpClient client){
        httpClientPool.returnObject(client);
    }

    @Override
    public void sendMessage(ChatPostMessageParams params) {
        LOGGER.info("Message sending {} .....", params.getChannelId());
        SlackClient slackClient = null;
        try {
            slackClient = getSlackClient();
            slackClient.postMessage(params);
        } catch (Exception e) {
            LOGGER.error("Message not sent", e);
        } finally {
            if(slackClient != null) {
                returnSlackClient(slackClient);
            }
        }
    }

    @Override
    public String getConversationIdByEmail(String userEmail) {
        Objects.requireNonNull(userEmail, "userId could not be null");
        SlackClient slackClient = null;
        UsersInfoResponse usersInfoResponse;
        try {
            slackClient = getSlackClient();
            usersInfoResponse = slackClient.lookupUserByEmail(UserEmailParams.builder()
                    .setEmail(userEmail)
                    .build()).join().unwrapOrElseThrow();
           return getOpenedConversationId(slackClient, usersInfoResponse.getUser().getId());
        } catch (Exception e) {
            LOGGER.error("Message not sent", e);
            return null;
        } finally {
            if(slackClient != null) {
                returnSlackClient(slackClient);
            }
        }
    }

    @Override
    public String getConversationIdBySlackId(String userSlackId){
        SlackClient slackClient = null;
        try {
            slackClient = getSlackClient();
            return getOpenedConversationId(slackClient, userSlackId);
        } catch (Exception e) {
            LOGGER.error("Message not sent", e);
            return null;
        } finally {
            if(slackClient != null) {
                returnSlackClient(slackClient);
            }
        }
    }

    @Override
    public void sendCompletionMessage(String userSlackId, String userFullName, String userEmail) {
        sendMessage(ChatPostMessageParams.builder()
                .setText("Processed....")
                .setChannelId(getConversationIdBySlackId(userSlackId))
                .addBlocks(Section.of(
                        Text.of(TextType.MARKDOWN, String.format("*Processed*: %s <%s>",
                                userFullName,
                                userEmail)))
                ).build());
    }

    @Override
    public void sendErrorMessageToInitiator(String userSlackId, String shortText, String text) {
        sendMessage(ChatPostMessageParams.builder()
                .setText(shortText)
                .setChannelId(getConversationIdBySlackId(userSlackId))
                .addBlocks(Section.of(Text.of(TextType.MARKDOWN, String.format("I see *Error*: %s \n ", text)))
                ).build());
    }

    @Override
    public void sendFile(String fileName, String userEmail) {
        LOGGER.info("File sending {} .....", fileName);
        try {
            String conversationId = getConversationIdByEmail(userEmail);
            if (conversationId == null) {
                throw new IllegalArgumentException("conversationIdWithUser could not be null");
            }
            postFile(fileName, getConversationIdByEmail(userEmail));
        } catch (Exception e) {
            LOGGER.error("Error during file sending", e);
        }
    }

    private static String getOpenedConversationId(SlackClient slackClient, String userId) {
        LOGGER.info("Getting conversation ID for {}", userId);
        ConversationsOpenResponse conversation = slackClient.openConversation(
                ConversationOpenParams.builder()
                        .addUsers(userId).setReturnIm(true).build()).join().unwrapOrElseThrow();
        return conversation.getConversation().getId();
    }

    public static void addIfNotNull(List<Field> fields, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            fields.add(Field.builder()
                    .setTitle(String.format("*%s*:", label))
                    .setValue(value)
                    .setIsShort(false)
                    .build());
        }
    }

    private void postFile(String fileName, String conversationId) throws Exception {
        File file = new File(fileName);
        Request request = getBuilder("POST").setUrl("https://slack.com/api/files.upload")
                .addQueryParam("channels", conversationId)
                .addQueryParam("initial_comment",
                        "Please *keep* this invoice for the next *three years*")
                .addBodyPart(new FilePart("file", file))
                .build();

        Response response = makeRequest(request);
        LOGGER.info("file upload response: {}", response.getResponseBody());
        LOGGER.info("file {} deleted {}", file.getName(), file.delete());
    }

    private Response makeRequest(Request request) throws Exception{
        Future<Response> responseFuture;
        AsyncHttpClient client = null;
        try {
            client = getHttpClient();
            responseFuture = client.executeRequest(request);
            return responseFuture.get();
        } finally {
            if (client != null) {
                returnHttpClientToPool(client);
            }
        }
    }

    @Override
    public SlackFileInfo getFileInfo(SlackEvent.FileMetaInfo fileMetaInfo) throws Exception {
        Request request = getBuilder("GET").setUrl("https://slack.com/api/files.info")
                .addQueryParam("file", fileMetaInfo.getId())
                .build();

        Response response = makeRequest(request);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(response.getResponseBody(), SlackFileInfo.class);
    }

    @Override
    public RequestBuilder getBuilder(String httpMethod) {
        return new RequestBuilder(httpMethod).addHeader("Authorization", "Bearer " + token);
    }

    @Override
    public void downloadFile(String fileName, SlackFileInfo slackFile) {
        Request request = getBuilder("GET").setUrl(slackFile.getFileMetaInfo().getUrlPrivate())
                .build();
        AsyncHttpClient client = null;
        try (FileOutputStream stream = new FileOutputStream(fileName)) {
            client = getHttpClient();
            ListenableFuture<FileOutputStream> responseListenableFuture = client.executeRequest(request,
                    new AsyncCompletionHandler<FileOutputStream>() {

                        @Override
                        public State onBodyPartReceived(HttpResponseBodyPart bodyPart)
                                throws Exception {
                            stream.getChannel().write(bodyPart.getBodyByteBuffer());
                            return State.CONTINUE;
                        }

                        @Override
                        public FileOutputStream onCompleted(Response response) {
                            return stream;
                        }
                    });

            responseListenableFuture.get();
            LOGGER.info("File downloaded");
        } catch (Exception e) {
            LOGGER.error("error during file close or download", e);
        } finally {
            if (client != null) {
                returnHttpClientToPool(client);
            }
        }
    }
}
