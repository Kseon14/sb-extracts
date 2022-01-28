package com.am.sbextracts.service;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.pool.HttpClientPool;
import com.am.sbextracts.pool.SlackClientPool;
import com.am.sbextracts.service.integration.SlackClientWrapper;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.am.sbextracts.vo.View;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatUpdateRequest;
import com.slack.api.methods.request.conversations.ConversationsOpenRequest;
import com.slack.api.methods.request.files.FilesInfoRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.methods.response.conversations.ConversationsOpenResponse;
import com.slack.api.methods.response.files.FilesInfoResponse;
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
import org.apache.commons.collections4.CollectionUtils;
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

    @SneakyThrows
    @Override
    @SbExceptionHandler
    public void sendMessage(ChatPostMessageRequest request, String userEmail, String initiatorSlackId) {
        log.info("Message sending {}...", request.getChannel());
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            CompletableFuture<ChatPostMessageResponse> response =
                    wrapper.getClient().chatPostMessage(request);
            handleError(response.get(), userEmail, initiatorSlackId);
        }
    }

    @SneakyThrows
    @Override
    @SbExceptionHandler
    public ChatPostMessageResponse sendMessage(ChatPostMessageRequest request, String initiatorSlackId) {
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            CompletableFuture<ChatPostMessageResponse> response = wrapper.getClient().chatPostMessage(request);
            return handleError(response.get(), initiatorSlackId);
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
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId("sectionId")
                                        .label(PlainTextObject.builder().text("Bamboo FolderID").build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId("gFolderId")
                                        .label(PlainTextObject.builder().text("Google folder ID").build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
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
            handleError(wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                    .view(view).build()).get(), slackInteractiveEvent.getUserId());
        }
    }

    private static void handleError(ViewsOpenResponse viewsOpenResponse, String userId) {
        ErrorResponseMetadata responseMetadata = viewsOpenResponse.getResponseMetadata();
        if (responseMetadata != null) {
            throw new SbExtractsException(String.format("Error: %s", responseMetadata.getMessages()), userId);
        }
    }

    private static ChatPostMessageResponse handleError(ChatPostMessageResponse response, String userId) {
        List<String> errors = response.getErrors();
        if (CollectionUtils.isNotEmpty(errors)) {
            throw new SbExtractsException(String.format("Error: %s", errors), userId);
        }
        return response;
    }

    private static void handleError(ChatPostMessageResponse response, String userEmail, String userId) {
        List<String> errors = response.getErrors();
        if (CollectionUtils.isNotEmpty(errors)) {
            throw new SbExtractsException(String.format("Error: %s", errors), userEmail, userId);
        }
    }

    private static void handleError(ChatUpdateResponse response, String userId) {
        String responseError = response.getError();
        if (responseError != null) {
            throw new SbExtractsException(String.format("Error: %s", responseError), userId);
        }
    }

    @SneakyThrows
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
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                InputBlock.builder().blockId("sectionId")
                                        .label(PlainTextObject.builder().text("Bamboo FolderID").build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
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
            handleError(wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                    .view(view).build()).get(), slackInteractiveEvent.getTriggerId());
        }
    }

    @SneakyThrows
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
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId("sectionId")
                                        .label(PlainTextObject.builder().text("Bamboo FolderID").build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
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
            handleError(wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                    .view(view).build()).get(), slackInteractiveEvent.getTriggerId());
        }
    }

    @SneakyThrows
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
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId("gFolderId")
                                        .label(PlainTextObject.builder().text("Google folder ID").build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
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
            handleError(wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                    .view(view).build()).get(), slackInteractiveEvent.getTriggerId());
        }
    }

    @SneakyThrows
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
                                                .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                                .build(),
                                        InputBlock.builder().blockId("sectionId")
                                                .label(PlainTextObject.builder().text("NS FolderID").build())
                                                .element(PlainTextInputElement.builder().actionId(FIELD).build())
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
            handleError(wrapper.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                    .view(view).build()).get(), slackInteractiveEvent.getTriggerId());
        }
    }


    @SneakyThrows
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
            if (usersInfoResponse.getError() != null) {
                throw new SbExtractsException(String.format("Couldn't get userInfo from slack: %s", usersInfoResponse.getError()),
                        userEmail, initiatorSlackId);
            }
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
            throw new SbExtractsException("userSlackId could not be null", "Initiator");
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
    public ChatPostMessageResponse sendMessageToInitiator(String initiatorSlackId, ChatPostMessageRequest.ChatPostMessageRequestBuilder builder) {
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

    @SneakyThrows
    public void updateMessage(com.slack.api.methods.response.chat.ChatPostMessageResponse initialMessage, String text, String initiatorSlackId) {
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            handleError(wrapper.getClient().chatUpdate(ChatUpdateRequest.builder()
                    .channel(initialMessage.getChannel())
                    .blocks(List.of(SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text(text).build()).build()))
                    .ts(initialMessage.getTs())
                    .build()).get(), initiatorSlackId);
        }
    }

    @SneakyThrows
    public void updateMessage(ChatUpdateRequest.ChatUpdateRequestBuilder builder, String initiatorSlackId) {
        try (SlackClientWrapper wrapper = new SlackClientWrapper(slackClientPool)) {
            handleError(wrapper.getClient().chatUpdate(builder
                    .build()).get(), initiatorSlackId);
        }
    }

    @Override
    public void sendErrorMessageToInitiator(String userSlackId, String shortText, String text) {
        sendMessage(getRequest(userSlackId, shortText, String.format("`%s`", text)), userSlackId);
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

    @SneakyThrows
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
            if (conversation.getError() != null) {
                throw new SbExtractsException(String.format("Could not get opened conversation id: %s", conversation.getError()),
                        userEmail, initiatorSlackId);
            }
            return conversation.getChannel().getId();
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
    public com.slack.api.model.File getFile(SlackEvent.FileMetaInfo fileMetaInfo) throws Exception {
        FilesInfoResponse fileInfo = new SlackClientWrapper(slackClientPool).getClient().filesInfo(FilesInfoRequest.builder().file(fileMetaInfo.getId()).build())
                .get();
        if (fileInfo.getError() != null) {
            throw new SbExtractsException("Could not get fileInfo", fileMetaInfo.getAuthor());
        }
        return fileInfo.getFile();
    }

    private RequestBuilder getBuilder(String httpMethod) {
        return new RequestBuilder(httpMethod).addHeader("Authorization", "Bearer " + token);
    }

    @Override
    @SbExceptionHandler
    public void downloadFile(String fileName, com.slack.api.model.File slackFile) {
        Request request = getBuilder("GET").setUrl(slackFile.getUrlPrivateDownload())
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
                    slackFile.getUser());
        } finally {
            returnHttpClientToPool(client);
        }
    }
}
