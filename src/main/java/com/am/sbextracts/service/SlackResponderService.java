package com.am.sbextracts.service;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;

@Service(value="slackService")
public class SlackResponderService implements ResponderService {

    private final static Logger LOGGER = LoggerFactory.getLogger(SlackResponderService.class);

    private final HttpClientPool httpClientPool;
    private final SlackClientPool slackClientPool;

    @Resource(name="slackService")
    SlackResponderService slackService;

    @Value("${slack.token}")
    private String token;

    public SlackResponderService(HttpClientPool httpClientPool, SlackClientPool slackClientPool) {
        this.httpClientPool = httpClientPool;
        this.slackClientPool = slackClientPool;
    }

    private SlackClient getSlackClient() throws Exception {
        SlackClient slackClient = slackClientPool.borrowObject();
        LOGGER.info("slack Client {}", slackClient.toString());
        return slackClient;
    }

    private void returnSlackClient(SlackClient client) {
        if (client != null) {
            slackClientPool.returnObject(client);
        }
    }

    private AsyncHttpClient getHttpClient() throws Exception {
        AsyncHttpClient client = httpClientPool.borrowObject();
        LOGGER.info("http Client {}", client.toString());
        return client;
    }

    private void returnHttpClientToPool(AsyncHttpClient client) {
        if (client != null) {
            httpClientPool.returnObject(client);
        }
    }

    @Override
    @SbExceptionHandler
    public void sendMessage(ChatPostMessageParams params, String userEmail, String initiatorSlackId) {
        LOGGER.info("Message sending {}...", params.getChannelId());
        SlackClient slackClient = null;
        try {
            slackClient = getSlackClient();
            slackClient.postMessage(params);
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, userEmail, initiatorSlackId);
        } finally {
            returnSlackClient(slackClient);
        }
    }

    @Override
    @SbExceptionHandler
    public String getConversationIdByEmail(String userEmail, String initiatorSlackId) {
        if (userEmail == null) {
            throw new SbExtractsException("userEmail could not be null", userEmail, initiatorSlackId);
        }
        SlackClient slackClient = null;
        UsersInfoResponse usersInfoResponse;
        try {
            slackClient = getSlackClient();
            usersInfoResponse = slackClient.lookupUserByEmail(UserEmailParams.builder()
                    .setEmail(userEmail)
                    .build()).join().unwrapOrElseThrow();
        } catch (Exception e) {
            throw new SbExtractsException("Couldn't get userInfo from slack", e, userEmail, initiatorSlackId);
        } finally {
            returnSlackClient(slackClient);
        }
        return getOpenedConversationId(usersInfoResponse.getUser().getId(), initiatorSlackId, userEmail);
    }

    @Override
    @SbExceptionHandler
    @Cacheable(value = "conversationIds", key = "#userSlackId",
            condition="#userSlackId!=null", unless = "#result== null")
    public String getConversationIdBySlackId(String userSlackId, String initiatorSlackId) {
        LOGGER.info("getting conversationId by slackId...");
        if (userSlackId == null) {
            throw new SbExtractsException("userSlackId could not be null", "Initiator", "Initiator");
        }
        return getOpenedConversationId(userSlackId, initiatorSlackId, "Initiator");
    }

    @Override
    public void sendCompletionMessage(String initiatorSlackId, String userFullName, String userEmail) {
        sendMessage(ChatPostMessageParams.builder()
                .setText("Processed...")
                .setChannelId(slackService.getConversationIdBySlackId(initiatorSlackId, initiatorSlackId))
                .addBlocks(Section.of(
                        Text.of(TextType.MARKDOWN, String.format("*Processed*: %s <%s>",
                                userFullName,
                                userEmail)))
                ).build(), userEmail, initiatorSlackId);
    }

    @Override
    public void sendErrorMessageToInitiator(String userSlackId, String shortText, String text) {
        sendMessage(ChatPostMessageParams.builder()
                .setText(shortText)
                .setChannelId(slackService.getConversationIdBySlackId(userSlackId, userSlackId))
                .addBlocks(Section.of(Text.of(TextType.MARKDOWN, String.format("`%s`", text)))
                ).build(), "Initiator", userSlackId);
    }

    @Override
    @SbExceptionHandler
    public void sendFile(String fileName, String userEmail, String initiatorSlackId) {
        LOGGER.info("File sending {}...", fileName);
        String conversationId = getConversationIdByEmail(userEmail, initiatorSlackId);
        if (conversationId == null) {
            throw new SbExtractsException("conversationIdWithUser could not be null", userEmail, initiatorSlackId);
        }
        postFile(fileName, conversationId, userEmail, initiatorSlackId);
    }

    @SbExceptionHandler
    private String getOpenedConversationId(String userId, String initiatorSlackId, String userEmail) {
        if (userId == null) {
            throw new SbExtractsException("userId could not be null", userEmail, initiatorSlackId);
        }
        LOGGER.info("Getting conversation ID for {}", userId);
        SlackClient slackClient = null;
        try {
            slackClient = getSlackClient();
            ConversationsOpenResponse conversation = slackClient.openConversation(
                    ConversationOpenParams.builder()
                            .addUsers(userId).setReturnIm(true).build()).join().unwrapOrElseThrow();
            return conversation.getConversation().getId();
        } catch (Exception e) {
            throw new SbExtractsException("Could not get opened conversation id", e, userEmail, initiatorSlackId);
        } finally {
            returnSlackClient(slackClient);
        }
    }

    public static void addIfNotNull(List<Field> fields, String label, String value) {
        if (isNotEmptyOrZero.test(value)) {
            fields.add(Field.builder()
                    .setTitle(String.format("*%s*:", label))
                    .setValue(value)
                    .setIsShort(false)
                    .build());
        }
    }

    private final static Predicate<String> isNotEmptyOrZero = input ->
            StringUtils.isNotBlank(input)
            && !StringUtils.equals(input, "0")
            && !StringUtils.equals(input, "0.0")
            && !StringUtils.equals(input, "0.00");

    private void postFile(String fileName, String conversationId, String userEmail, String initiatorSlackId) {
        File file = new File(fileName);
        Request request = getBuilder("POST").setUrl("https://slack.com/api/files.upload")
                .addQueryParam("channels", conversationId)
                .addQueryParam("initial_comment",
                        "Please *keep* this invoice for the next *three years*")
                .addBodyPart(new FilePart("file", file))
                .build();

        Response response;
        try {
            response = makeRequest(request);
        } catch (Exception e) {
            throw new SbExtractsException("Could not send file", e, userEmail, initiatorSlackId);
        }
        LOGGER.info("file upload response: {}", response.getResponseBody());
        LOGGER.info("file {} deleted {}", file.getName(), file.delete());
    }

    private Response makeRequest(Request request) throws Exception {
        Future<Response> responseFuture;
        AsyncHttpClient client = null;
        try {
            client = getHttpClient();
            responseFuture = client.executeRequest(request);
            return responseFuture.get();
        } finally {
            returnHttpClientToPool(client);
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

    private RequestBuilder getBuilder(String httpMethod) {
        return new RequestBuilder(httpMethod).addHeader("Authorization", "Bearer " + token);
    }

    @Override
    @SbExceptionHandler
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
            throw new SbExtractsException("error during file close or download", e, "Initiator",
                    slackFile.getFileMetaInfo().getAuthor());
        } finally {
            returnHttpClientToPool(client);
        }
    }
}
