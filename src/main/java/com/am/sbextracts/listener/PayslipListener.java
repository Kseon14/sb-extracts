package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.SlackResponderService;
import com.am.sbextracts.vo.Payslip;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.Field;
import com.slack.api.model.block.DividerBlock;
import com.slack.api.model.block.HeaderBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.event.EventListener;
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
public class PayslipListener {

	private final ResponderService slackResponderService;

	@EventListener
	public void onApplicationEvent(Payslip payslip) {
		try {
			TimeUnit.SECONDS.sleep(DEFAULT_DELAY);
		}
		catch (InterruptedException e) {
			log.error("sleep was interrupted", e);
			Thread.currentThread().interrupt();
		}
		Calendar cal = Calendar.getInstance();
		String month = new SimpleDateFormat("MMM").format(cal.getTime());

		List<LayoutBlock> blockList = new ArrayList<>();
		blockList.add(HeaderBlock.builder()
				.text(PlainTextObject.builder()
						.text(String.format("Payslip for %s", payslip.getFullName())).build()
				).build());
		blockList.add(SectionBlock.builder()
				.text(MarkdownTextObject.builder()
						.text(String.format(":wave: Hi, %s!\n"
										+ "Information about payment :dollar: for *%s* is below\n"
										+ "If you have any questions, please, contact <@%s> :paw_prints:",
								payslip.getFullName(),
								month,
								payslip.getAuthorSlackId())).build()).build());
		blockList.add(DividerBlock.builder().build());
		SlackResponderService.addBlockIfNotNull(blockList, "Contract Rate", payslip.getContractRate());
		SlackResponderService.addBlockIfNotNull(blockList, "Other Income", payslip.getOtherIncome());
		SlackResponderService.addBlockIfNotNull(blockList, "Reimbursement", payslip.getBonus());
		SlackResponderService.addBlockIfNotNull(blockList, "Social Tax", payslip.getSocialTax());
		SlackResponderService.addBlockIfNotNull(blockList, "Insurance", payslip.getInsurance());
		SlackResponderService.addBlockIfNotNull(blockList, "Currency Exchange Rate", payslip.getCurrencyRate());
		if (payslip.getTotalGross() == null) {
			SlackResponderService.addBlockIfNotNull(blockList, "Total Net", payslip.getTotalNet());
		}
		if (payslip.getTotalGross() != null) {
			SlackResponderService.addBlockIfNotNull(blockList, "Current Payment Tax", payslip.getCurrentPaymentTax());
		}
		SlackResponderService.addBlockIfNotNull(blockList, "Total Gross(with Bank fee)", payslip.getTotalGross());

		String conversationIdWithUser = slackResponderService.getConversationIdByEmail(payslip.getUserEmail(),
				payslip.getAuthorSlackId());
		slackResponderService.sendMessage(
				ChatPostMessageRequest.builder()
						.channel(conversationIdWithUser)
						.blocks(blockList)
						.build(),
				payslip.getUserEmail(), payslip.getAuthorSlackId());

		slackResponderService.sendMessageToInitiator(payslip.getAuthorSlackId(), payslip.getFullName(),
				payslip.getUserEmail());
	}
}
