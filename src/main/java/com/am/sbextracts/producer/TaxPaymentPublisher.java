package com.am.sbextracts.producer;

import com.am.sbextracts.service.SlackResponderService;
import com.am.sbextracts.service.XslxProcessorService;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.TaxPayment;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class TaxPaymentPublisher implements Publisher {
    private final Logger LOGGER = LoggerFactory.getLogger(TaxPaymentPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;
    private final SlackResponderService slackResponderService;

    @Autowired
    public TaxPaymentPublisher(ApplicationEventPublisher applicationEventPublisher,
                               SlackResponderService slackResponderService) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.slackResponderService = slackResponderService;
    }

    @Override
    public void produce(XSSFWorkbook workbook, SlackEvent.FileMetaInfo fileMetaInfo) {
        XSSFSheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            try {
            String firstCell = XslxProcessorService.getCell(row, "A", evaluator);
            if (firstCell != null) {
                TaxPayment taxPayment = new TaxPayment(this);
                taxPayment.setTaxCode(firstCell);
                taxPayment.setFullName(XslxProcessorService.getCell(row, "B", evaluator));
                taxPayment.setAmount(XslxProcessorService.getCell(row, "C", evaluator));
                taxPayment.setBankName(XslxProcessorService.getCell(row, "D", evaluator));
                taxPayment.setMfo(XslxProcessorService.getCell(row, "E", evaluator));
                taxPayment.setReceiver(XslxProcessorService.getCell(row, "F", evaluator));
                taxPayment.setAccount(XslxProcessorService.getCell(row, "G", evaluator));
                taxPayment.setCode(XslxProcessorService.getCell(row, "H", evaluator));
                taxPayment.setPurposeOfPayment(XslxProcessorService.getCell(row, "I", evaluator));
                taxPayment.setUserEmail(XslxProcessorService.getCell(row, "J", evaluator));
                taxPayment.setDueDate(XslxProcessorService.getDateFromCell(row, "K"));
                taxPayment.setTaxType(XslxProcessorService.getCell(row, "L", evaluator));
                taxPayment.setAuthorSlackId(fileMetaInfo.getAuthor());
                LOGGER.info("Tax payment: {}", taxPayment);
                applicationEventPublisher.publishEvent(taxPayment);
            }
            } catch (UnsupportedOperationException e) {
                slackResponderService.sendErrorMessageToInitiator(fileMetaInfo.getAuthor(),
                        "Error during processing", e.getMessage());
            }
        }
    }
}
