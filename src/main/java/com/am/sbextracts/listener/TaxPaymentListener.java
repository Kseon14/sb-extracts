package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.SlackResponderService;
import com.am.sbextracts.vo.TaxPayment;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.Field;
import com.slack.api.model.block.DividerBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.am.sbextracts.listener.GlobalVariables.DEFAULT_DELAY;

@Component
public class TaxPaymentListener implements ApplicationListener<TaxPayment> {

    private final ResponderService slackResponderService;

    @Autowired
    public TaxPaymentListener(ResponderService slackResponderService) {
        this.slackResponderService = slackResponderService;
    }

    @Override
    @SneakyThrows
    public void onApplicationEvent(TaxPayment taxPayment) {
        TimeUnit.SECONDS.sleep(DEFAULT_DELAY);

        String conversationIdWithUser = slackResponderService.getConversationIdByEmail(taxPayment.getUserEmail(),
                taxPayment.getAuthorSlackId());

        List<Field> fieldList = new ArrayList<>();
        SlackResponderService.addIfNotNull(fieldList, "Сума", taxPayment.getAmount());
        SlackResponderService.addIfNotNull(fieldList, "Отримувач", taxPayment.getReceiver());
        SlackResponderService.addIfNotNull(fieldList, "Розрахунковий рахунок", taxPayment.getAccount());
        SlackResponderService.addIfNotNull(fieldList, "ЄДРПОУ", taxPayment.getCode());
        SlackResponderService.addIfNotNull(fieldList, "Призначення платежу", taxPayment.getPurposeOfPayment());

        slackResponderService.sendMessage(
                ChatPostMessageRequest.builder()
                        .text(String.format("Дані для оплати %s", taxPayment.getTaxType()))
                        .channel(conversationIdWithUser)
                        .blocks(List.of(SectionBlock.builder()
                                        .text(MarkdownTextObject.builder()
                                                .text(String.format(":wave: Привіт, %s!\n"
                                                                + "Дані для оплати :dollar: *%s* нижче \n"
                                                                + ":date: Термін сплати до *%s* \n"
                                                                + "У випадку виникнення питань, зверніться до <@%s> :paw_prints:",
                                                        taxPayment.getFullName(),
                                                        taxPayment.getTaxType(),
                                                        new SimpleDateFormat("dd MMM").format(taxPayment.getDueDate()),
                                                        taxPayment.getAuthorSlackId())).build()).build(),
                                DividerBlock.builder().build()))
                        .attachments(List.of(
                                com.slack.api.model.Attachment.builder()
                                        .fields(fieldList).color("#36a64f").build()))
                        .build(), taxPayment.getUserEmail(), taxPayment.getAuthorSlackId());

        slackResponderService.sendMessageToInitiator(taxPayment.getAuthorSlackId(), taxPayment.getFullName(), taxPayment.getUserEmail());

    }
}
