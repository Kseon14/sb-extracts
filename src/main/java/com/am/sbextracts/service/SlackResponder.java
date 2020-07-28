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
        for(Person person : persons) {
            UsersInfoResponse usersInfoResponse = slackClient.lookupUserByEmail(UserEmailParams.builder()
                            .setEmail(person.getUserName())
                            .build()).join().unwrapOrElseThrow();
            Result<ConversationsOpenResponse, SlackError> message = slackClient.openConversation(ConversationOpenParams.builder()
                    .addUsers(usersInfoResponse.getUser().getId()).setReturnIm(true).build()).join();

            message.ifOk(e ->  slackClient.postMessage(
                    ChatPostMessageParams.builder()
                            .setText("Here is an example message with blocks:")
                            .setUsername(usersInfoResponse.getUser().getId())
                            .setChannelId(e.getConversation().getId())
                            .addBlocks(
                                    Section.of(Text.of(TextType.MARKDOWN, " :wave: Привет "+ person.getFullName()+"!\n"
                                            + "данные для оплаты :dollar: *" + person.getTaxType() + "* ниже \n" +
                                            " :date: | Срок оплаты до *" + person.getDueDate() +"*")),
                                    Divider.builder().build(),
                                    Section.of(Text.of(TextType.MARKDOWN, String.format("*Сумма:* %s", person.getAmount()) + "\n" +
                                    String.format("*Банк получателя:* %s", person.getBankName()) + "\n" +
                                    String.format("*МФО:* %s", person.getMfo()) + "\n" +
                                    String.format("*Получатель:* %s", person.getReceiver()) + "\n" +
                                    String.format("*Расчетный счет:* %s", person.getAccount()) + "\n" +
                                    String.format("*ЕДРПОУ:* %s", person.getCode()) + "\n" +
                                    String.format("*Назначение платежа:* %s", person.getPurposeOfPayment())))
                            ).build()));
        }
    }

    private SlackClient getSlackClient(){
        SlackClientRuntimeConfig runtimeConfig = SlackClientRuntimeConfig.builder()
                .setTokenSupplier(() -> token)
                .build();

        return SlackClientFactory.defaultFactory().build(runtimeConfig);
    }

}
