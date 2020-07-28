package com.am.sbextracts.service;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.am.sbextracts.vo.Person;
import com.hubspot.algebra.Result;
import com.hubspot.slack.client.SlackClient;
import com.hubspot.slack.client.SlackClientFactory;
import com.hubspot.slack.client.SlackClientRuntimeConfig;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.methods.params.conversations.ConversationOpenParams;
import com.hubspot.slack.client.methods.params.im.ImOpenParams;
import com.hubspot.slack.client.methods.params.users.UserEmailParams;
import com.hubspot.slack.client.models.Attachment;
import com.hubspot.slack.client.models.Field;
import com.hubspot.slack.client.models.blocks.Divider;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import com.hubspot.slack.client.models.response.SlackError;
import com.hubspot.slack.client.models.response.chat.ChatPostMessageResponse;
import com.hubspot.slack.client.models.response.conversations.ConversationsOpenResponse;
import com.hubspot.slack.client.models.response.im.ImOpenResponse;
import com.hubspot.slack.client.models.response.users.UsersInfoResponse;

@Service
public class SlackResponder implements Responder {

    @Value("${slack.token}")
    private String token;

    @Override
    public void respond(Collection<Person> persons) {
        SlackClient slackClient = getSlackClient();
        for (Person person : persons) {
            UsersInfoResponse usersInfoResponse = slackClient.lookupUserByEmail(UserEmailParams.builder()
                    .setEmail(person.getUserName())
                    .build()).join().unwrapOrElseThrow();
            Result<ConversationsOpenResponse, SlackError> conversation = slackClient.openConversation(
                    ConversationOpenParams.builder()
                            .addUsers(usersInfoResponse.getUser().getId()).setReturnIm(true).build()).join();

            conversation.ifOk(e -> slackClient.postMessage(
                    ChatPostMessageParams.builder()
                            .setText(String.format("Данные для оплаты %s", person.getTaxType()))
                            .setUsername(usersInfoResponse.getUser().getId())
                            .setChannelId(e.getConversation().getId())
                            .addBlocks(Section.of(
                                    Text.of(TextType.MARKDOWN, String.format(" :wave: Привет %s!\n"
                                                    + "данные для оплаты :dollar: *%s* ниже \n"
                                                    + ":date: Срок оплаты до *%s*",
                                            person.getFullName(),
                                            person.getTaxType(),
                                            person.getDueDate()))),
                                    Divider.builder().build()

                            )
                            .addAttachments(
                                    Attachment.builder().addFields(Field.builder()
                                                    .setTitle("*Сумма:*")
                                                    .setValue(person.getAmount())
                                                    .setIsShort(false)
                                                    .build(),
                                            Field.builder()
                                                    .setTitle("*Банк получателя:*")
                                                    .setValue(person.getBankName())
                                                    .setIsShort(false)
                                                    .build(),
                                            Field.builder()
                                                    .setTitle("*МФО:*")
                                                    .setValue(person.getMfo())
                                                    .setIsShort(false)
                                                    .build(),
                                            Field.builder()
                                                    .setTitle("*Получатель:*")
                                                    .setValue(person.getReceiver())
                                                    .setIsShort(false)
                                                    .build(),
                                            Field.builder()
                                                    .setTitle("*Расчетный счет:*")
                                                    .setValue(person.getAccount())
                                                    .setIsShort(false)
                                                    .build(),
                                            Field.builder()
                                                    .setTitle("*ЕДРПОУ:*")
                                                    .setValue(person.getCode())
                                                    .setIsShort(false)
                                                    .build(),
                                            Field.builder()
                                                    .setTitle("*Назначение платежа:*")
                                                    .setValue(person.getPurposeOfPayment())
                                                    .setIsShort(false)
                                                    .build()
                                    ).setColor("#ff0000").build()
                            ).build()));
        }
    }

    private SlackClient getSlackClient() {
        SlackClientRuntimeConfig runtimeConfig = SlackClientRuntimeConfig.builder()
                .setTokenSupplier(() -> token)
                .build();

        return SlackClientFactory.defaultFactory().build(runtimeConfig);
    }

}
