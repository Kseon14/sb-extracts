package com.am.sbextracts.service;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.pool.HttpClientPool;
import com.am.sbextracts.pool.SlackClientPool;
import com.am.sbextracts.service.integration.SlackClientWrapper;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackFileInfo;
import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.am.sbextracts.vo.View;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.algebra.Result;
import com.hubspot.slack.client.SlackClient;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.methods.params.chat.ChatUpdateMessageParams;
import com.hubspot.slack.client.methods.params.conversations.ConversationOpenParams;
import com.hubspot.slack.client.methods.params.users.UserEmailParams;
import com.hubspot.slack.client.methods.params.views.OpenViewParams;
import com.hubspot.slack.client.models.Field;
import com.hubspot.slack.client.models.blocks.Input;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.elements.DatePicker;
import com.hubspot.slack.client.models.blocks.elements.PlainTextInput;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import com.hubspot.slack.client.models.response.SlackError;
import com.hubspot.slack.client.models.response.chat.ChatPostMessageResponse;
import com.hubspot.slack.client.models.response.chat.ChatUpdateMessageResponse;
import com.hubspot.slack.client.models.response.conversations.ConversationsOpenResponse;
import com.hubspot.slack.client.models.response.users.UsersInfoResponse;
import com.hubspot.slack.client.models.views.ModalViewPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.request.body.multipart.FilePart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Predicate;

@Slf4j
@Service(value="slackService")
@RequiredArgsConstructor
public class SlackResponderService implements ResponderService {

    private final String FIELD = "field";

    private final HttpClientPool httpClientPool;
    private final SlackClientPool slackClientPool;

    @Resource(name="slackService")
    SlackResponderService slackService;

    @Value("${slack.token}")
    private final String token;

    private AsyncHttpClient getHttpClient() throws Exception {
        AsyncHttpClient client = httpClientPool.borrowObject();
        log.info("http Client {}", client.toString());
        return client;
    }

    private void returnHttpClientToPool(AsyncHttpClient client) {
        if (client != null) {
            httpClientPool.returnObject(client);
        }
    }

    @Override
    @SbExceptionHandler
    public ChatPostMessageResponse sendMessage(ChatPostMessageParams params, String userEmail, String initiatorSlackId) {
        log.info("Message sending {}...", params.getChannelId());
        try(SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            CompletableFuture<Result<ChatPostMessageResponse, SlackError>> response = wrapper.getClient().postMessage(params);
            return response.get().unwrapOrElseThrow();
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, userEmail, initiatorSlackId);
        }
    }

    @Override
    @SbExceptionHandler
    public ChatPostMessageResponse sendMessage(ChatPostMessageParams params, String initiatorSlackId) {
        log.info("Message sending {}...", params.getChannelId());
        try(SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            CompletableFuture<Result<ChatPostMessageResponse, SlackError>> response = wrapper.getClient().postMessage(params);
            return response.get().unwrapOrElseThrow();
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, initiatorSlackId);
        }
    }

    @Override
    @SbExceptionHandler
    public void sendMarkupView(SlackInteractiveEvent slackInteractiveEvent) {
        ModalViewPayload modalViewPayload =
                ModalViewPayload.builder()
                        .setExternalId(View.ModalActionType.MARKUP.name())
                        .setTitle(Text.of(TextType.PLAIN_TEXT,"Markup and Send for Sign"))
                        .setSubmitButtonText(Text.of(TextType.PLAIN_TEXT, "Start"))
                        .addBlocks(Input.builder().setBlockId("sessionId")
                                .setLabel(Text.of(TextType.PLAIN_TEXT,"SessionID"))
                                .setElement(PlainTextInput.of(FIELD))
                                .build())
                        .addBlocks(Input.builder().setBlockId("sectionId")
                                .setLabel(Text.of(TextType.PLAIN_TEXT,"Bamboo FolderID"))
                                .setElement(PlainTextInput.of(FIELD))
                                .build())
                        .addBlocks(Input.builder().setBlockId("totalToProcessing")
                                .setLabel(Text.of(TextType.PLAIN_TEXT,"Total Documents to Process"))
                                .setElement(PlainTextInput.of(FIELD))
                                .build())
                        .addBlocks(Section.builder()
                                .setBlockId("date")
                                .setText(Text.of(TextType.MARKDOWN, "Pick a date for Acts"))
                                .setAccessory(DatePicker.builder()
                                        .setActionId(FIELD)
                                        .setInitialDate(LocalDate.now())
                                        .setPlaceholder(Text.of(TextType.PLAIN_TEXT,"Select a date"))
                                        .build())
                                .build())
                        .build();
        try(SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().openView(OpenViewParams.of(slackInteractiveEvent.getTrigger_id(), modalViewPayload));
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, slackInteractiveEvent.getUser_id());
        }
    }

    @Override
    @SbExceptionHandler
    public void sendDebtors(SlackInteractiveEvent slackInteractiveEvent) {
        ModalViewPayload modalViewPayload =
                ModalViewPayload.builder()
                        .setExternalId(View.ModalActionType.DEBTORS.name())
                        .setTitle(Text.of(TextType.PLAIN_TEXT,"Markup and Send for Sign"))
                        .setSubmitButtonText(Text.of(TextType.PLAIN_TEXT, "Start"))
                        .addBlocks(Input.builder().setBlockId("sessionId")
                                .setLabel(Text.of(TextType.PLAIN_TEXT,"SessionID"))
                                .setElement(PlainTextInput.of(FIELD))
                                .build())
                        .addBlocks(Input.builder().setBlockId("sectionId")
                                .setLabel(Text.of(TextType.PLAIN_TEXT,"Bamboo FolderID"))
                                .setElement(PlainTextInput.of(FIELD))
                                .build())
                        .addBlocks(Section.builder()
                                .setBlockId("date")
                                .setText(Text.of(TextType.MARKDOWN, "Pick a date for Acts"))
                                .setAccessory(DatePicker.builder()
                                        .setActionId(FIELD)
                                        .setInitialDate(LocalDate.now())
                                        .setPlaceholder(Text.of(TextType.PLAIN_TEXT,"Select a date"))
                                        .build())
                                .build())
                        .build();
        try(SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().openView(OpenViewParams.of(slackInteractiveEvent.getTrigger_id(), modalViewPayload));
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, slackInteractiveEvent.getTrigger_id());
        }
    }

    @Override
    @SbExceptionHandler
    public void sendDownloadSigned(SlackInteractiveEvent slackInteractiveEvent) {
        ModalViewPayload modalViewPayload =
                ModalViewPayload.builder()
                        .setExternalId(View.ModalActionType.SIGNED.name())
                        .setTitle(Text.of(TextType.PLAIN_TEXT,"Markup and Send for Sign"))
                        .setSubmitButtonText(Text.of(TextType.PLAIN_TEXT, "Start"))
                        .addBlocks(Input.builder().setBlockId("sessionId")
                                .setLabel(Text.of(TextType.PLAIN_TEXT,"SessionID"))
                                .setElement(PlainTextInput.of(FIELD))
                                .build())
                        .addBlocks(Input.builder().setBlockId("gFolderId")
                                .setLabel(Text.of(TextType.PLAIN_TEXT,"Google folder ID"))
                                .setElement(PlainTextInput.of(FIELD))
                                .build())
                        .addBlocks(Section.builder()
                                .setBlockId("date")
                                .setText(Text.of(TextType.MARKDOWN, "Pick a date for Acts"))
                                .setAccessory(DatePicker.builder()
                                        .setActionId(FIELD)
                                        .setInitialDate(LocalDate.now())
                                        .setPlaceholder(Text.of(TextType.PLAIN_TEXT,"Select a date"))
                                        .build())
                                .build())
                        .build();
        try(SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            log.info("trigger id {}", slackInteractiveEvent.getTrigger_id());
            wrapper.getClient().openView(OpenViewParams.of(slackInteractiveEvent.getTrigger_id(), modalViewPayload));
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, slackInteractiveEvent.getUser_id());
        }
    }


    @Override
    @SbExceptionHandler
    public String getConversationIdByEmail(String userEmail, String initiatorSlackId) {
        if (userEmail == null) {
            throw new SbExtractsException("userEmail could not be null", userEmail, initiatorSlackId);
        }
        UsersInfoResponse usersInfoResponse;
        try(SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            usersInfoResponse = wrapper.getClient().lookupUserByEmail(UserEmailParams.builder()
                    .setEmail(userEmail)
                    .build()).join().unwrapOrElseThrow();
        } catch (Exception e) {
            throw new SbExtractsException("Couldn't get userInfo from slack", e, userEmail, initiatorSlackId);
        }
        return getOpenedConversationId(usersInfoResponse.getUser().getId(), initiatorSlackId, userEmail);
    }

    @Override
    @SbExceptionHandler
    @Cacheable(value = "conversationIds", key = "#userSlackId",
            condition="#userSlackId!=null", unless = "#result== null")
    public String getConversationIdBySlackId(String userSlackId, String initiatorSlackId) {
        log.info("getting conversationId by slackId...");
        if (userSlackId == null) {
            throw new SbExtractsException("userSlackId could not be null", "Initiator", "Initiator");
        }
        return getOpenedConversationId(userSlackId, initiatorSlackId, "Initiator");
    }

    @Override
    public void sendMessageToInitiator(String initiatorSlackId, String userFullName, String userEmail) {
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
    public ChatPostMessageResponse sendMessageToInitiator(String initiatorSlackId, ChatPostMessageParams.Builder builder) {
        return sendMessage(builder
                .setChannelId(slackService.getConversationIdBySlackId(initiatorSlackId, initiatorSlackId))
                .build(), initiatorSlackId);
    }

    public void updateMessage(ChatPostMessageResponse initialMessage, String text, String initiatorSlackId){
        try(SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().updateMessage(ChatUpdateMessageParams.builder()
                    .setChannelId(initialMessage.getChannel())
                    .addBlocks(Section.of(
                            Text.of(TextType.MARKDOWN, text)))
                    .setTs(initialMessage.getTs())
                    .build()).get().unwrapOrElseThrow();
        } catch (Exception e) {
            throw new SbExtractsException("Could not get opened conversation id", e, initiatorSlackId);
        }
    }

    public void updateMessage(ChatUpdateMessageParams.Builder builder, String initiatorSlackId){
        try(SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().updateMessage(builder
                    .build()).get().unwrapOrElseThrow();
        } catch (Exception e) {
            throw new SbExtractsException("Could not get opened conversation id", e, initiatorSlackId);
        }
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
        log.info("File sending {}...", fileName);
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
        log.info("Getting conversation ID for {}", userId);

        try(SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            ConversationsOpenResponse conversation = wrapper.getClient().openConversation(
                    ConversationOpenParams.builder()
                            .addUsers(userId).setReturnIm(true).build()).join().unwrapOrElseThrow();
            return conversation.getConversation().getId();
        } catch (Exception e) {
            throw new SbExtractsException("Could not get opened conversation id", e, userEmail, initiatorSlackId);
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
        log.info("file upload response: {}", response.getResponseBody());
        log.info("file {} deleted {}", file.getName(), file.delete());
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
                    new AsyncCompletionHandler<>() {

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
            log.info("File downloaded");
        } catch (Exception e) {
            throw new SbExtractsException("error during file close or download", e, "Initiator",
                    slackFile.getFileMetaInfo().getAuthor());
        } finally {
            returnHttpClientToPool(client);
        }
    }
}
