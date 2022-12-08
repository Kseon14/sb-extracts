package com.am.sbextracts.listener;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.SlackResponderService;
import com.am.sbextracts.service.integration.GmailService;
import com.am.sbextracts.vo.TaxPayment;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.Field;
import com.slack.api.model.block.DividerBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.am.sbextracts.listener.GlobalVariables.DEFAULT_DELAY;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaxPaymentListener {

    @Value("${app.fromMail}")
    String from;

    private final ResponderService slackResponderService;
    private final GmailService gmailService;

    @EventListener
    @SbExceptionHandler
    public void onApplicationEvent(TaxPayment taxPayment) {
        try {
            TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
        } catch (InterruptedException e) {
            log.error("sleep was interrupted", e);
            Thread.currentThread().interrupt();
        }

        String authorSlackId = taxPayment.getAuthorSlackId();
        String conversationIdWithUser = slackResponderService.getConversationIdByEmail(taxPayment.getUserEmail(),
                authorSlackId);

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
                                                        authorSlackId)).build()).build(),
                                DividerBlock.builder().build()))
                        .attachments(List.of(
                                com.slack.api.model.Attachment.builder()
                                        .fields(fieldList).color("#36a64f").build()))
                        .build(), taxPayment.getUserEmail(), authorSlackId);

        sendMail(taxPayment, authorSlackId);
        slackResponderService.sendMessageToInitiator(authorSlackId, taxPayment.getFullName(),
                taxPayment.getUserEmail());
    }

    private void sendMail(TaxPayment taxPayment, String authorSlackId) {
        if (!taxPayment.isWithEmail()) {
            return;
        }
        Set<String> emails = new HashSet<>(taxPayment.getAdditionalUserEmail());
        emails.add(taxPayment.getUserEmail());
        try {
            gmailService.sendMessage(emails,
                    String.format("Дані для оплати %s, сплата до %s", taxPayment.getTaxType(),
                            new SimpleDateFormat("dd MMM").format(taxPayment.getDueDate())),
                    getMailBody(String.format("Привіт, %s!<br>"
                                            + "Дані для оплати <b>%s</b> нижче <br>"
                                            + "Термін сплати до <b>%s</b> <br>"
                                            + "У випадку виникнення питань, зверніться до <a href = \"mailto: %s\">мене</a><br> ",
                                    taxPayment.getFullName(),
                                    taxPayment.getTaxType(),
                                    new SimpleDateFormat("dd MMM").format(taxPayment.getDueDate()),
                                    from),
                            taxPayment.getAmount(),
                            taxPayment.getReceiver(),
                            taxPayment.getAccount(),
                            taxPayment.getCode(),
                            taxPayment.getPurposeOfPayment()
                    ),
                    authorSlackId);
        } catch (Exception e) {
            throw new SbExtractsException("Email could not be sent", e, authorSlackId);
        }
    }

    private static String getMailBody(String messageHeader,
                                      String amount,
                                      String receiver,
                                      String account,
                                      String code,
                                      String purposeOfPayment) {
        return String.format("<p>%s</p>" +
                        "<head> <style>\n" +
                        "        .vertical {\n" +
                        "            border-left: 5px solid black;\n" +
                        "            height: 100%%;\n" +
                        "            border-color: green;\n" +
                        "        }\n" +
                        "    </style>\n" +
                        "</head>\n" +
                        "<table style=\"height:200px;border-collapse: collapse; border-style: none;\" border=\"0\">\n" +
                        "<tbody>\n" +
                        "<tr style=\"height:20px;padding: 3px;\">\n" +
                        "<td style=\"width:10px;height: 100%%;\" rowspan=\"5\">\n" +
                        "<div class= \"vertical\">&nbsp;</div></td>\n" +
                        "<td >\n" +
                        "<b>Сума:</b><br> %s\n" +
                        "</td>\n" +
                        "</tr>\n" +
                        "<tr style=\"height:20px;padding: 5px;\">\n" +
                        "<td >\n" +
                        "<b>Отримувач:</b><br> %s\n" +
                        "</td>\n" +
                        "</tr>\n" +
                        "<tr style=\"height:20px;padding:5px;\">\n" +
                        "<td >\n" +
                        "<b>Розрахунковий рахунок:</b><br> %s\n" +
                        "</td>\n" +
                        "</tr>\n" +
                        "<tr style=\"height:20px;padding: 5px;\">\n" +
                        "<td >\n" +
                        "<b>ЄДРПОУ:</b><br> %s\n" +
                        "</td>\n" +
                        "</tr>\n" +
                        "<tr style=\"height:20px;padding: 5px;\">\n" +
                        "<td>\n" +
                        "<b>Призначення платежу:</b><br> %s\n" +
                        "</td>\n" +
                        "</tr>\n" +
                        "</tbody>\n" +
                        "</table>",
                messageHeader, amount, receiver, account, code, purposeOfPayment);
    }
}
