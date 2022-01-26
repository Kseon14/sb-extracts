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
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatUpdateRequest;
import com.slack.api.methods.request.conversations.ConversationsOpenRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsOpenResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.ErrorResponseMetadata;
import com.slack.api.model.Field;
import com.slack.api.model.block.InputBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.DatePickerElement;
import com.slack.api.model.block.element.PlainTextInputElement;
import com.slack.api.model.view.ViewSubmit;
import com.slack.api.model.view.ViewTitle;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackResponderService implements ResponderService {

    private final static String FIELD = "field";
    private final static String PLAIN_TEXT = "plain_text";
    private final static String MODAL = "modal";

    private final HttpClientPool httpClientPool;
    private final SlackClientPool slackClientPool;

    @Lazy
    private final SlackResponderService slackService;

    @Value("${slack.token}")
    private final String token;

    private AsyncHttpClient getHttpClient() throws Exception {
        AsyncHttpClient client = httpClientPool.borrowObject();
        log.debug("http Client {}", client.toString());
        return client;
    }

    private void returnHttpClientToPool(AsyncHttpClient client) {
        if (client != null) {
            httpClientPool.returnObject(client);
        }
    }

    @Override
    @SbExceptionHandler
    public void sendMessage(ChatPostMessageRequest params, String userEmail, String initiatorSlackId) {
        log.info("Message sending {}...", params.getChannel());
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            CompletableFuture<ChatPostMessageResponse> response =
                    wrapper.getClient().chatPostMessage(params);
            response.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new SbExtractsException("Message not sent to:", e, userEmail, initiatorSlackId);
        }
    }

    @Override
    @SbExceptionHandler
    public ChatPostMessageResponse sendMessage(ChatPostMessageRequest params, String initiatorSlackId) {
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            CompletableFuture<ChatPostMessageResponse> response = wrapper.getClient().chatPostMessage(params);
            return response.get();
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, initiatorSlackId);
        }
    }

    @SneakyThrows
    @Override
    @SbExceptionHandler
    public void sendMarkupView(SlackInteractiveEvent slackInteractiveEvent) {
        com.slack.api.model.view.View view =
                com.slack.api.model.view.View.builder()
                        .type(MODAL)
                        .callbackId(View.ModalActionType.MARKUP.name())
                        .title(ViewTitle.builder().type(PLAIN_TEXT).text("Markup and Send for Sign").build())
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text("Start").build())
                        .blocks(List.of(InputBlock.builder()
                                        .blockId("sessionId")
                                        .label(PlainTextObject.builder().text("SessionID").build())
                                        .element(PlainTextInputElement.builder().build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId("sectionId")
                                        .label(PlainTextObject.builder().text("Bamboo FolderID").build())
                                        .element(PlainTextInputElement.builder().build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId("gFolderId")
                                        .label(PlainTextObject.builder().text("Google folder ID").build())
                                        .element(PlainTextInputElement.builder().build())
                                        .build(),
                                SectionBlock.builder()
                                        .blockId("date")
                                        .text(MarkdownTextObject.builder().text("Pick a date for Acts").build())
                                        .accessory(DatePickerElement.builder()
                                                .actionId(FIELD)
                                                .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                                .placeholder(PlainTextObject.builder().text("Select a date").build())
                                                .build())
                                        .build()))
                        .build();
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            handleError(wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTrigger_id())
                    .view(view).build()).get(), slackInteractiveEvent.getUser_id());
        }
    }

    private static void handleError(ViewsOpenResponse viewsOpenResponse, String userId) {
        ErrorResponseMetadata responseMetadata = viewsOpenResponse.getResponseMetadata();
        if (responseMetadata != null) {
            throw new SbExtractsException(String.format("Error: %s", responseMetadata.getMessages()), userId);
        }
    }

    @Override
    @SbExceptionHandler
    public void sendDebtors(SlackInteractiveEvent slackInteractiveEvent) {
        com.slack.api.model.view.View view =
                com.slack.api.model.view.View.builder()
                        .type(MODAL)
                        .callbackId(View.ModalActionType.DEBTORS.name())
                        .title(ViewTitle.builder().type(PLAIN_TEXT).text("Get Debtor List").build())
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text("Start").build())
                        .blocks(List.of(InputBlock.builder().blockId("sessionId")
                                        .label(PlainTextObject.builder().text("SessionID").build())
                                        .element(PlainTextInputElement.builder().build())
                                        .build(),
                                InputBlock.builder().blockId("sectionId")
                                        .label(PlainTextObject.builder().text("Bamboo FolderID").build())
                                        .element(PlainTextInputElement.builder().build())
                                        .build(),
                                SectionBlock.builder()
                                        .blockId("date")
                                        .text(MarkdownTextObject.builder().text("Pick a date for Acts").build())
                                        .accessory(DatePickerElement.builder()
                                                .actionId(FIELD)
                                                .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                                .placeholder(PlainTextObject.builder().text("Select a date").build())
                                                .build())
                                        .build()))
                        .build();
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTrigger_id())
                    .view(view).build());
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, slackInteractiveEvent.getTrigger_id());
        }
    }

    @Override
    @SbExceptionHandler
    public void pushDebtors(SlackInteractiveEvent slackInteractiveEvent) {
        com.slack.api.model.view.View view =
                com.slack.api.model.view.View.builder()
                        .type(MODAL)
                        .callbackId(View.ModalActionType.PUSH_DEBTORS.name())
                        .title(ViewTitle.builder().type(PLAIN_TEXT).text("Push debtors").build())
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text("Start").build())
                        .blocks(List.of(InputBlock.builder()
                                        .blockId("sessionId")
                                        .label(PlainTextObject.builder().text("SessionID").build())
                                        .element(PlainTextInputElement.builder().build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId("sectionId")
                                        .label(PlainTextObject.builder().text("Bamboo FolderID").build())
                                        .element(PlainTextInputElement.builder().build())
                                        .build(),
                                SectionBlock.builder()
                                        .blockId("date")
                                        .text(MarkdownTextObject.builder().text("Pick a date for Acts").build())
                                        .accessory(DatePickerElement.builder()
                                                .actionId(FIELD)
                                                .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                                .placeholder(PlainTextObject.builder().text("Select a date").build())
                                                .build())
                                        .build()))
                        .build();
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTrigger_id())
                    .view(view).build());
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, slackInteractiveEvent.getTrigger_id());
        }
    }

    @Override
    @SbExceptionHandler
    public void sendDownloadSigned(SlackInteractiveEvent slackInteractiveEvent) {
        com.slack.api.model.view.View view =
                com.slack.api.model.view.View.builder()
                        .type(MODAL)
                        .callbackId(View.ModalActionType.SIGNED.name())
                        .title(ViewTitle.builder().type(PLAIN_TEXT).text("Download Signed files").build())
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text("Start").build())
                        .blocks(List.of(InputBlock.builder().blockId("sessionId")
                                        .label(PlainTextObject.builder().text("SessionID").build())
                                        .element(PlainTextInputElement.builder().build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId("gFolderId")
                                        .label(PlainTextObject.builder().text("Google folder ID").build())
                                        .element(PlainTextInputElement.builder().build())
                                        .build(),
                                SectionBlock.builder()
                                        .blockId("date")
                                        .text(MarkdownTextObject.builder().text("Pick a date for Acts").build())
                                        .accessory(DatePickerElement.builder()
                                                .actionId(FIELD)
                                                .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                                .placeholder(PlainTextObject.builder().text("Select a date").build())
                                                .build())
                                        .build()))
                        .build();
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTrigger_id())
                    .view(view).build());
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, slackInteractiveEvent.getUser_id());
        }
    }

    @Override
    @SbExceptionHandler
    public void sendDownloadInvoice(SlackInteractiveEvent slackInteractiveEvent) {
        com.slack.api.model.view.View view =
                com.slack.api.model.view.View.builder()
                        .type(MODAL)
                        .callbackId(View.ModalActionType.INVOICE_DOWNLOAD.name())
                        .title(ViewTitle.builder().type(PLAIN_TEXT).text("Download Invoice files").build())
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text("Start").build())
                        .blocks(
                                List.of(InputBlock.builder()
                                                .blockId("sessionId")
                                                .label(PlainTextObject.builder().text("NS SessionID").build())
                                                .element(PlainTextInputElement.builder().build())
                                                .build(),
                                        InputBlock.builder().blockId("sectionId")
                                                .label(PlainTextObject.builder().text("NS FolderID").build())
                                                .element(PlainTextInputElement.builder().build())
                                                .build(),
                                        SectionBlock.builder()
                                                .blockId("date")
                                                .text(MarkdownTextObject.builder().text("Pick a date for Acts").build())
                                                .accessory(DatePickerElement.builder()
                                                        .actionId(FIELD)
                                                        .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                                        .placeholder(PlainTextObject.builder().text("Select a date").build())
                                                        .build())
                                                .build()))
                        .build();
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTrigger_id())
                    .view(view).build());
        } catch (Exception e) {
            throw new SbExtractsException("Message not sent to:", e, slackInteractiveEvent.getUser_id());
        }
    }


    @Override
    @SbExceptionHandler
    public String getConversationIdByEmail(String userEmail, String initiatorSlackId) {
        if (userEmail == null) {
            throw new SbExtractsException("userEmail could not be null", initiatorSlackId);
        }
        UsersLookupByEmailResponse usersInfoResponse;
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            usersInfoResponse = wrapper.getClient().usersLookupByEmail(UsersLookupByEmailRequest.builder()
                    .email(userEmail)
                    .build()).get();
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
        log.debug("getting conversationId by slackId...");
        if (userSlackId == null) {
            throw new SbExtractsException("userSlackId could not be null", "Initiator", "Initiator");
        }
        return getOpenedConversationId(userSlackId, initiatorSlackId, "Initiator");
    }

    @Override
    public void sendMessageToInitiator(String initiatorSlackId, String userFullName, String userEmail) {
        sendMessage(getRequest(initiatorSlackId, "Processed...", String.format("*Processed*: %s <%s>",
                userFullName,
                userEmail)), userEmail, initiatorSlackId);
    }

    @Override
    public com.slack.api.methods.response.chat.ChatPostMessageResponse sendMessageToInitiator(String initiatorSlackId, ChatPostMessageRequest.ChatPostMessageRequestBuilder builder) {
        return sendMessage(builder
                .channel(slackService.getConversationIdBySlackId(initiatorSlackId, initiatorSlackId))
                .build(), initiatorSlackId);
    }

    @Override
    public void log(String initiatorSlackId, String text) {
        sendMessage(getRequest(initiatorSlackId, text, text), initiatorSlackId);
    }

    private ChatPostMessageRequest getRequest(String initiatorSlackId, String shortText, String text) {
        return ChatPostMessageRequest.builder()
                .text(shortText)
                .blocks(List.of(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text(text).build()).build()))
                .channel(slackService.getConversationIdBySlackId(initiatorSlackId, initiatorSlackId))
                .build();
    }

    public void updateMessage(com.slack.api.methods.response.chat.ChatPostMessageResponse initialMessage, String text, String initiatorSlackId) {
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().chatUpdate(ChatUpdateRequest.builder()
                    .channel(initialMessage.getChannel())
                    .blocks(List.of(SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text(text).build()).build()))
                    .ts(initialMessage.getTs())
                    .build()).get();
        } catch (Exception e) {
            throw new SbExtractsException("Could not get opened conversation id", e, initiatorSlackId);
        }
    }

    public void updateMessage(ChatUpdateRequest.ChatUpdateRequestBuilder builder, String initiatorSlackId) {
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            wrapper.getClient().chatUpdate(builder
                    .build()).get();
        } catch (Exception e) {
            throw new SbExtractsException("Could not get opened conversation id", e, initiatorSlackId);
        }
    }

    @Override
    public void sendErrorMessageToInitiator(String userSlackId, String shortText, String text) {
        sendMessage(ChatPostMessageRequest.builder()
                .text(shortText)
                .channel(slackService.getConversationIdBySlackId(userSlackId, userSlackId))
                .blocks(List.of(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text(String.format("`%s`", text)).build()).build())
                ).build(), userSlackId);
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
            ConversationsOpenResponse conversation = wrapper.getClient().conversationsOpen(
                    ConversationsOpenRequest.builder()
                            .users(List.of(userId)).returnIm(true).build()).get();
            return conversation.getChannel().getId();
        } catch (Exception e) {
            throw new SbExtractsException("Could not get opened conversation id", e, userEmail, initiatorSlackId);
        }
    }

    public static void addIfNotNull(List<Field> fields, String label, String value) {
        if (isNotEmptyOrZero.test(value)) {
            fields.add(Field.builder()
                    .title(String.format("*%s*:", label))
                    .value(value)
                    .valueShortEnough(false)
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
