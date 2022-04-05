package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.SlackResponderService;
import com.am.sbextracts.vo.Payslip;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.Field;
import com.slack.api.model.block.DividerBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.am.sbextracts.listener.GlobalVariables.DEFAULT_DELAY;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayslipListener implements ApplicationListener<Payslip> {

    private final ResponderService slackResponderService;

    @Override
    @SneakyThrows
    public void onApplicationEvent(Payslip payslip) {
        try {
            TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
        } catch (InterruptedException e) {
            log.error("sleep was interrupted", e);
            Thread.currentThread().interrupt();
        }
        Calendar cal = Calendar.getInstance();
        String month = new SimpleDateFormat("MMM").format(cal.getTime());

        List<Field> fieldList = new ArrayList<>();
        SlackResponderService.addIfNotNull(fieldList, "Contract Rate", payslip.getContractRate());
        SlackResponderService.addIfNotNull(fieldList, "Other Income", payslip.getOtherIncome());
        SlackResponderService.addIfNotNull(fieldList, "Bonus", payslip.getBonus());
        SlackResponderService.addIfNotNull(fieldList, "Social Tax", payslip.getSocialTax());
        SlackResponderService.addIfNotNull(fieldList, "Insurance", payslip.getInsurance());
        SlackResponderService.addIfNotNull(fieldList, "Currency Exchange Rate", payslip.getCurrencyRate());
        if (payslip.getTotalGross() == null) {
            SlackResponderService.addIfNotNull(fieldList, "Total Net", payslip.getTotalNet());
        }
        if (payslip.getTotalGross() != null) {
            SlackResponderService.addIfNotNull(fieldList, "Current Payment Tax", payslip.getCurrentPaymentTax());
        }
        SlackResponderService.addIfNotNull(fieldList, "Total Gross(with Bank fee)", payslip.getTotalGross());

        String conversationIdWithUser = slackResponderService.getConversationIdByEmail(payslip.getUserEmail(), payslip.getAuthorSlackId());
        slackResponderService.sendMessage(
                ChatPostMessageRequest.builder()
                        .text(String.format("Payslip for %s", payslip.getFullName()))
                        .channel(conversationIdWithUser)
                        .blocks(List.of(SectionBlock.builder()
                                        .text(MarkdownTextObject.builder()
                                                .text(String.format(":wave: Hi, %s!\n"
                                                                + "Information about payment :dollar: for *%s* is below\n"
                                                                + "If you have any questions, please, contact <@%s> :paw_prints:",
                                                        payslip.getFullName(),
                                                        month,
                                                        payslip.getAuthorSlackId())).build()).build(),
                                DividerBlock.builder().build()))
                        .attachments(List.of(com.slack.api.model.Attachment.builder()
                                .fields(fieldList).color("#3655c7").build()))
                        .build(),
                payslip.getUserEmail(), payslip.getAuthorSlackId());

        slackResponderService.sendMessageToInitiator(payslip.getAuthorSlackId(), payslip.getFullName(), payslip.getUserEmail());
    }
}
