package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.SlackResponderService;
import com.am.sbextracts.vo.TaxPayment;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.models.Attachment;
import com.hubspot.slack.client.models.Field;
import com.hubspot.slack.client.models.blocks.Divider;
import com.hubspot.slack.client.models.blocks.Section;
import com.hubspot.slack.client.models.blocks.objects.Text;
import com.hubspot.slack.client.models.blocks.objects.TextType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Component
public class TaxPaymentListener implements ApplicationListener<TaxPayment> {

    private final ResponderService slackResponderService;

    @Autowired
    public TaxPaymentListener(ResponderService slackResponderService) {
        this.slackResponderService = slackResponderService;
    }

    @Override
    public void onApplicationEvent(TaxPayment taxPayment) {

        String conversationIdWithUser = slackResponderService.getConversationIdByEmail(taxPayment.getUserEmail());
        if (conversationIdWithUser == null) {
            throw new IllegalArgumentException("conversationIdWithUser could not be null");
        }

        List<Field> fieldList = new ArrayList<>();
        SlackResponderService.addIfNotNull(fieldList, "Сума", taxPayment.getAmount());
        SlackResponderService.addIfNotNull(fieldList, "Банк отримувач", taxPayment.getBankName());
        SlackResponderService.addIfNotNull(fieldList, "МФО", taxPayment.getMfo());
        SlackResponderService.addIfNotNull(fieldList, "Отримувач", taxPayment.getReceiver());
        SlackResponderService.addIfNotNull(fieldList, "Розрахунковий рахунок", taxPayment.getAccount());
        SlackResponderService.addIfNotNull(fieldList, "ЄДРПОУ", taxPayment.getCode());
        SlackResponderService.addIfNotNull(fieldList, "Призначення платежу", taxPayment.getPurposeOfPayment());

        slackResponderService.sendMessage(
                ChatPostMessageParams.builder()
                        .setText(String.format("Дані для оплати %s", taxPayment.getTaxType()))
                        .setChannelId(conversationIdWithUser)
                        .addBlocks(Section.of(
                                Text.of(TextType.MARKDOWN, String.format(":wave: Привіт, %s!\n"
                                                + "Дані для оплати :dollar: *%s* нижче \n"
                                                + ":date: Термін сплати до *%s* \n"
                                                + "У випадку виникнення питань, зверніться до <@%s> :paw_prints:",
                                        taxPayment.getFullName(),
                                        taxPayment.getTaxType(),
                                        new SimpleDateFormat("dd MMM").format(taxPayment.getDueDate()),
                                        taxPayment.getAuthorSlackId()))),
                                Divider.builder().build()
                        ).addAttachments(Attachment.builder().setFields(fieldList).setColor("#36a64f").build())
                        .build());

        slackResponderService.sendCompletionMessage(taxPayment.getAuthorSlackId(), taxPayment.getFullName(), taxPayment.getUserEmail());

    }
}
