package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.SlackResponderService;
import com.am.sbextracts.vo.Payslip;
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
import java.util.Calendar;
import java.util.List;

@Component
public class PayslipListener implements ApplicationListener<Payslip> {

    private final ResponderService slackResponderService;

    @Autowired
    public PayslipListener(ResponderService slackResponderService) {
        this.slackResponderService = slackResponderService;
    }

    @Override
    public void onApplicationEvent(Payslip payslip) {
        Calendar cal = Calendar.getInstance();
        String month = new SimpleDateFormat("MMM").format(cal.getTime());

        List<Field> fieldList = new ArrayList<>();
        SlackResponderService.addIfNotNull(fieldList, "Contract Rate", payslip.getContractRate());
        SlackResponderService.addIfNotNull(fieldList, "Other Income", payslip.getOtherIncome());
        SlackResponderService.addIfNotNull(fieldList, "Social Tax", payslip.getSocialTax());
        SlackResponderService.addIfNotNull(fieldList, "Insurance", payslip.getInsurance());
        SlackResponderService.addIfNotNull(fieldList, "Rent", payslip.getRent());
        SlackResponderService.addIfNotNull(fieldList, "Currency Exchange Rate", payslip.getCurrencyRate());
        if( payslip.getTotalGross() == null) {
            SlackResponderService.addIfNotNull(fieldList, "Total Net", payslip.getTotalNet());
        }
        if (payslip.getTotalGross() != null) {
            SlackResponderService.addIfNotNull(fieldList, "Current Payment Tax", payslip.getCurrentPaymentTax());
        }
        SlackResponderService.addIfNotNull(fieldList, "Total Gross(with Bank fee)", payslip.getTotalGross());

        String conversationIdWithUser = slackResponderService.getConversationIdByEmail(payslip.getUserEmail(), payslip.getAuthorSlackId());
        slackResponderService.sendMessage(
                ChatPostMessageParams.builder()
                        .setText(String.format("Payslip for %s", payslip.getFullName()))
                        .setChannelId(conversationIdWithUser)
                        .addBlocks(Section.of(
                                Text.of(TextType.MARKDOWN, String.format(":wave: Hi, %s!\n"
                                                + "Information about payment :dollar: for *%s* is below\n"
                                                + "If you have any questions, please, contact <@%s> :paw_prints:",
                                        payslip.getFullName(),
                                        month,
                                        payslip.getAuthorSlackId()))),
                                Divider.builder().build()
                        ).addAttachments(Attachment.builder().setFields(fieldList).setColor("#3655c7").build())
                        .build(), payslip.getUserEmail(), payslip.getAuthorSlackId());

        slackResponderService.sendCompletionMessage(payslip.getAuthorSlackId(), payslip.getFullName(), payslip.getUserEmail());
    }


}
