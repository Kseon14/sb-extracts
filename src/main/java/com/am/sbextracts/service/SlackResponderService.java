package com.am.sbextracts.service;

import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackFileInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.slack.client.SlackClient;
import com.hubspot.slack.client.SlackClientFactory;
import com.hubspot.slack.client.SlackClientRuntimeConfig;
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
import org.asynchttpclient.Dsl;
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
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Service
public class SlackResponderService implements ResponderService {

    private final Logger LOGGER = LoggerFactory.getLogger(SlackResponderService.class);

    @Value("${slack.token}")
    private String token;

    private SlackClient getSlackClient() {
        SlackClientRuntimeConfig runtimeConfig = SlackClientRuntimeConfig.builder()
                .setTokenSupplier(() -> token)
                .build();

        return SlackClientFactory.defaultFactory().build(runtimeConfig);
    }

    @Override
    public void sendMessage(ChatPostMessageParams params) {
        getSlackClient().postMessage(params);
    }

    @Override
    public String getConversationIdByEmail(String userEmail) {
        Objects.requireNonNull(userEmail, "userId could not be null");
        SlackClient slackClient = getSlackClient();
        UsersInfoResponse usersInfoResponse = slackClient.lookupUserByEmail(UserEmailParams.builder()
                .setEmail(userEmail)
                .build()).join().unwrapOrElseThrow();
        return getOpenedConversationId(slackClient, usersInfoResponse.getUser().getId());
    }

    @Override
    public String getConversationIdBySlackId(String userSlackId){
        return getOpenedConversationId(getSlackClient(), userSlackId);
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
    public void sendFile(String fileName, String userEmail) {
        try {
            postFile(fileName, getConversationIdByEmail(userEmail));
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.error("Error during file sending", e);
        }
    }

    private static String getOpenedConversationId(SlackClient slackClient, String userId) {
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

    private void postFile(String fileName, String conversationId) throws ExecutionException,
            InterruptedException {
        File file = new File(fileName);
        Request request = getBuilder("POST").setUrl("https://slack.com/api/files.upload")
                .addQueryParam("channels", conversationId)
                .addQueryParam("initial_comment",
                        "Please *keep* this invoice for the next *three years*")
                .addBodyPart(new FilePart("file", file))
                .build();
        Future<Response> responseFuture = getHttpClient().executeRequest(request);
        responseFuture.get();
        LOGGER.info("file {} deleted {}", file.getName(), file.delete());
    }

    private AsyncHttpClient getHttpClient(){
        return Dsl.asyncHttpClient();
    }

    @Override
    public SlackFileInfo getFileInfo(SlackEvent.FileMetaInfo fileMetaInfo)
            throws ExecutionException, InterruptedException, JsonProcessingException {

        Request request = getBuilder("GET").setUrl("https://slack.com/api/files.info")
                .addQueryParam("file", fileMetaInfo.getId())
                .build();
        Future<Response> responseFuture = getHttpClient().executeRequest(request);
        Response response = responseFuture.get();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(response.getResponseBody(), SlackFileInfo.class);
    }

    @Override
    public RequestBuilder getBuilder(String httpMethod) {
        return new RequestBuilder(httpMethod).addHeader("Authorization", "Bearer " + token);
    }

    @Override
    public void downloadFile(String fileName, SlackFileInfo slackFile) throws ExecutionException, InterruptedException {
        Request request = getBuilder("GET").setUrl(slackFile.getFileMetaInfo().getUrlPrivate())
                .build();
        try (FileOutputStream stream = new FileOutputStream(fileName)) {
            ListenableFuture<FileOutputStream> responseListenableFuture = getHttpClient().executeRequest(request,
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

        } catch (IOException e) {
            LOGGER.error("error during file close", e);
        }
    }
}
