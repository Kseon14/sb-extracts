package com.am.sbextracts.service;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.model.SlackResponse;
import com.am.sbextracts.pool.HttpClientPool;
import com.am.sbextracts.pool.SlackClient;
import com.am.sbextracts.vo.FileMetaInfo;
import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.am.sbextracts.vo.View;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.methods.SlackApiException;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackResponderService implements ResponderService {

    private static final String FIELD = "field";
    private static final String PLAIN_TEXT = "plain_text";
    private static final String MODAL = "modal";

    private static final Predicate<String> isNotEmptyOrZero = input ->
            StringUtils.isNotBlank(input)
                    && !StringUtils.equals(input, "0")
                    && !StringUtils.equals(input, "0.0")
                    && !StringUtils.equals(input, "0.00");
    public static final String START = "Start";
    public static final String SESSION_ID = "sessionId";
    public static final String SECTION_ID = "sectionId";
    public static final String BAMBOO_FOLDER_ID = "Bamboo FolderID";
    public static final String PICK_A_DATE_FOR_ACTS = "Pick a date for Acts";
    public static final String SELECT_A_DATE = "Select a date";
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    private final HttpClientPool httpClientPool;
    private final ObjectMapper objectMapper;

    private final SlackClient client;

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
    public void sendMessage(ChatPostMessageRequest request, String userEmail, String initiatorSlackId) {
        log.info("Message sending {}...", request.getChannel());
        try {
            ChatPostMessageResponse response =
                    client.getClient().chatPostMessage(request);
            handleError(response, userEmail, initiatorSlackId);
        } catch (SlackApiException | IOException ex) {
            throw new SbExtractsException(String.format("Couldn't send message to: %s", userEmail), ex,
                    userEmail, initiatorSlackId);
        }
    }


    @Override
    @SbExceptionHandler
    public ChatPostMessageResponse sendMessage(ChatPostMessageRequest request, String initiatorSlackId) {
        ChatPostMessageResponse response;
        try {
            response = client.getClient().chatPostMessage(request);
        } catch (IOException | SlackApiException e) {
            throw new IllegalStateException(e);
        }
        return handleError(response, initiatorSlackId);

    }


    @Override
    @SbExceptionHandler
    public void sendMarkupView(SlackInteractiveEvent slackInteractiveEvent) {
        com.slack.api.model.view.View view =
                com.slack.api.model.view.View.builder()
                        .type(MODAL)
                        .callbackId(View.ModalActionType.MARKUP.name())
                        .title(ViewTitle.builder().type(PLAIN_TEXT).text("Markup and Send for Sign").build())
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text(START).build())
                        .blocks(List.of(InputBlock.builder()
                                        .blockId(SESSION_ID)
                                        .label(PlainTextObject.builder().text("SessionID").build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId(SECTION_ID)
                                        .label(PlainTextObject.builder().text(BAMBOO_FOLDER_ID).build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId("gFolderId")
                                        .label(PlainTextObject.builder().text("Google folder ID").build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                SectionBlock.builder()
                                        .blockId("date")
                                        .text(MarkdownTextObject.builder().text(PICK_A_DATE_FOR_ACTS).build())
                                        .accessory(DatePickerElement.builder()
                                                .actionId(FIELD)
                                                .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_PATTERN)))
                                                .placeholder(PlainTextObject.builder().text(SELECT_A_DATE).build())
                                                .build())
                                        .build()))
                        .build();

        try {
            handleError(client.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                    .view(view).build()), slackInteractiveEvent.getUserId());
        } catch (IOException | SlackApiException e) {
            throw new IllegalStateException(e);

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
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text(START).build())
                        .blocks(List.of(InputBlock.builder().blockId(SESSION_ID)
                                        .label(PlainTextObject.builder().text("SessionID").build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                InputBlock.builder().blockId(SECTION_ID)
                                        .label(PlainTextObject.builder().text(BAMBOO_FOLDER_ID).build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                SectionBlock.builder()
                                        .blockId("date")
                                        .text(MarkdownTextObject.builder().text(PICK_A_DATE_FOR_ACTS).build())
                                        .accessory(DatePickerElement.builder()
                                                .actionId(FIELD)
                                                .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_PATTERN)))
                                                .placeholder(PlainTextObject.builder().text(SELECT_A_DATE).build())
                                                .build())
                                        .build()))
                        .build();

        handleError(client.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                .view(view).build()), slackInteractiveEvent.getTriggerId());

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
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text(START).build())
                        .blocks(List.of(InputBlock.builder()
                                        .blockId(SESSION_ID)
                                        .label(PlainTextObject.builder().text("SessionID").build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                InputBlock.builder()
                                        .blockId(SECTION_ID)
                                        .label(PlainTextObject.builder().text(BAMBOO_FOLDER_ID).build())
                                        .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                        .build(),
                                SectionBlock.builder()
                                        .blockId("date")
                                        .text(MarkdownTextObject.builder().text(PICK_A_DATE_FOR_ACTS).build())
                                        .accessory(DatePickerElement.builder()
                                                .actionId(FIELD)
                                                .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_PATTERN)))
                                                .placeholder(PlainTextObject.builder().text(SELECT_A_DATE).build())
                                                .build())
                                        .build()))
                        .build();

        handleError(client.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                .view(view).build()), slackInteractiveEvent.getTriggerId());

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
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text(START).build())
                        .blocks(List.of(InputBlock.builder().blockId(SESSION_ID)
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
                                        .text(MarkdownTextObject.builder().text(PICK_A_DATE_FOR_ACTS).build())
                                        .accessory(DatePickerElement.builder()
                                                .actionId(FIELD)
                                                .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_PATTERN)))
                                                .placeholder(PlainTextObject.builder().text(SELECT_A_DATE).build())
                                                .build())
                                        .build()))
                        .build();

        handleError(client.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                .view(view).build()), slackInteractiveEvent.getTriggerId());

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
                        .submit(ViewSubmit.builder().type(PLAIN_TEXT).text(START).build())
                        .blocks(
                                List.of(InputBlock.builder()
                                                .blockId(SESSION_ID)
                                                .label(PlainTextObject.builder().text("NS SessionID").build())
                                                .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                                .build(),
                                        InputBlock.builder().blockId(SECTION_ID)
                                                .label(PlainTextObject.builder().text("NS FolderID").build())
                                                .element(PlainTextInputElement.builder().actionId(FIELD).build())
                                                .build(),
                                        SectionBlock.builder()
                                                .blockId("date")
                                                .text(MarkdownTextObject.builder().text(PICK_A_DATE_FOR_ACTS).build())
                                                .accessory(DatePickerElement.builder()
                                                        .actionId(FIELD)
                                                        .initialDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_PATTERN)))
                                                        .placeholder(PlainTextObject.builder().text(SELECT_A_DATE).build())
                                                        .build())
                                                .build()))
                        .build();

        handleError(client.getClient().viewsOpen(ViewsOpenRequest.builder().triggerId(slackInteractiveEvent.getTriggerId())
                .view(view).build()), slackInteractiveEvent.getTriggerId());

    }


    @Override
    @SbExceptionHandler
    public String getConversationIdByEmail(String userEmail, String initiatorSlackId) {
        if (userEmail == null) {
            throw new SbExtractsException("userEmail could not be null", initiatorSlackId);
        }
        UsersLookupByEmailResponse usersInfoResponse;
        try {
            usersInfoResponse = client.getClient().usersLookupByEmail(UsersLookupByEmailRequest.builder()
                    .email(userEmail)
                    .build());
            if (usersInfoResponse.getError() != null) {
                throw new SbExtractsException(String.format("Couldn't get userInfo from slack: %s", usersInfoResponse.getError()),
                        userEmail, initiatorSlackId);
            }
            log.info("user with email:{} found by Id: {}", userEmail, usersInfoResponse.getUser().getId());
        } catch (SlackApiException | IOException ex) {
            throw new SbExtractsException(String.format("Couldn't get userInfo from slack: %s", userEmail), ex,
                    userEmail, initiatorSlackId);
        }
        return getOpenedConversationId(usersInfoResponse.getUser().getId(), initiatorSlackId, userEmail);
    }

    @Override
    @SbExceptionHandler
    @Cacheable(value = "conversationIds", key = "#userSlackId",
            condition = "#userSlackId!=null", unless = "#result== null")
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


    public void updateMessage(com.slack.api.methods.response.chat.ChatPostMessageResponse initialMessage, String text, String initiatorSlackId) {

        try {
            handleError(client.getClient().chatUpdate(ChatUpdateRequest.builder()
                    .channel(initialMessage.getChannel())
                    .blocks(List.of(SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text(text).build()).build()))
                    .text("Updates...")
                    .ts(initialMessage.getTs())
                    .build()), initiatorSlackId);
        } catch (IOException | SlackApiException e) {
            throw new RuntimeException(e);
        }
    }


    public void updateMessage(ChatUpdateRequest.ChatUpdateRequestBuilder builder, String initiatorSlackId) {

        try {
            handleError(client.getClient().chatUpdate(builder
                    .build()), initiatorSlackId);
        } catch (IOException | SlackApiException e) {
            throw new IllegalStateException(e);
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

    @SbExceptionHandler
    private String getOpenedConversationId(String userId, String initiatorSlackId, String userEmail) {
        if (userId == null) {
            throw new SbExtractsException("userId could not be null", userEmail, initiatorSlackId);
        }
        log.info("Getting conversation ID for {}", userId);

        try {
            ConversationsOpenResponse conversation = client.getClient().conversationsOpen(
                    ConversationsOpenRequest.builder()
                            .users(List.of(userId)).returnIm(true).build());
            if (conversation.getError() != null) {
                throw new SbExtractsException(String.format("Could not get opened conversation id: %s", conversation.getError()),
                        userEmail, initiatorSlackId);
            }
            return conversation.getChannel().getId();
        } catch (SlackApiException | IOException ex) {
            throw new SbExtractsException(String.format("Couldn't get opened conversation from slack: %s", userEmail), ex,
                    userEmail, initiatorSlackId);
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
        try {
            Files.delete(Path.of(fileName));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        log.info("file {} deleted", file.getName());
        try {
            SlackResponse slackResponse = objectMapper.readValue(response.getResponseBody(), SlackResponse.class);
            if (!slackResponse.getOk()) {
                throw new SbExtractsException(String.format("Error during file posting: %s for %s", slackResponse.getError(), userEmail),
                        userEmail, initiatorSlackId);
            }
        } catch (JsonProcessingException e) {
            log.error("Error during deserialization of {}", response.getResponseBody());
        }
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
    public com.slack.api.model.File getFile(FileMetaInfo fileMetaInfo) throws Exception {
        FilesInfoResponse fileInfo = client.getClient().filesInfo(FilesInfoRequest.builder().file(fileMetaInfo.getId()).build());
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
                        public FileOutputStream onCompleted(org.asynchttpclient.Response response) {
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
