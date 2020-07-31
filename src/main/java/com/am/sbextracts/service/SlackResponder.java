package com.am.sbextracts.service;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.am.sbextracts.vo.Person;
import com.hubspot.algebra.Result;
import com.hubspot.slack.client.SlackClient;
import com.hubspot.slack.client.SlackClientFactory;
import com.hubspot.slack.client.SlackClientRuntimeConfig;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.methods.params.conversations.ConversationOpenParams;
import com.hubspot.slack.client.methods.params.users.UserEmailParams;
import com.hubspot.slack.client.models.Attachment;
import com.hubspot.slack.client.models.Field;
import com.hubspot.slack.client.models.blocks.Divider;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import com.hubspot.slack.client.models.response.SlackError;
import com.hubspot.slack.client.models.response.conversations.ConversationsOpenResponse;
import com.hubspot.slack.client.models.response.users.UsersInfoResponse;

@Service
public class SlackResponder implements Responder {

    private final Logger LOGGER = LoggerFactory.getLogger(SlackResponder.class);

    @Value("${slack.token}")
    private String token;

    @Override
    public void respond(Collection<Person> persons) {
        LOGGER.info("Date start broadcasting");
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
                            .setText(String.format("Дані для оплати %s", person.getTaxType()))
                            .setUsername(usersInfoResponse.getUser().getId())
                            .setChannelId(e.getConversation().getId())
                            .addBlocks(Section.of(
                                    Text.of(TextType.MARKDOWN, String.format(" :wave: Привіт %s!\n"
                                                    + "дані для оплати :dollar: *%s* нижче \n"
                                                    + ":date: Термін сплати до *%s* \n"
                                                    + "у випадку виникнення питань зверніться до <@%s> :paw_prints:",
                                            person.getFullName(),
                                            person.getTaxType(),
                                            person.getDueDate(),
                                            person.getAuthor()))),
                                    Divider.builder().build()

                            ).addAttachments(
                            Attachment.builder().addFields(Field.builder()
                                            .setTitle("*Сума:*")
                                            .setValue(person.getAmount())
                                            .setIsShort(false)
                                            .build(),
                                    Field.builder()
                                            .setTitle("*Банк отримувач:*")
                                            .setValue(person.getBankName())
                                            .setIsShort(false)
                                            .build(),
                                    Field.builder()
                                            .setTitle("*МФО:*")
                                            .setValue(person.getMfo())
                                            .setIsShort(false)
                                            .build(),
                                    Field.builder()
                                            .setTitle("*Отримувач:*")
                                            .setValue(person.getReceiver())
                                            .setIsShort(false)
                                            .build(),
                                    Field.builder()
                                            .setTitle("*Розрахунковий рахунок:*")
                                            .setValue(person.getAccount())
                                            .setIsShort(false)
                                            .build(),
                                    Field.builder()
                                            .setTitle("*ЄДРПОУ:*")
                                            .setValue(person.getCode())
                                            .setIsShort(false)
                                            .build(),
                                    Field.builder()
                                            .setTitle("*Призначення платежу:*")
                                            .setValue(person.getPurposeOfPayment())
                                            .setIsShort(false)
                                            .build()
                            ).setColor("#36a64f")
                                    .build()
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
