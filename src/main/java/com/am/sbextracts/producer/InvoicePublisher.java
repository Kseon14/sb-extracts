package com.am.sbextracts.producer;

import com.am.sbextracts.service.SlackResponderService;
import com.am.sbextracts.service.XslxProcessorService;
import com.am.sbextracts.vo.Invoice;
import com.am.sbextracts.vo.SlackEvent;
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
public class InvoicePublisher implements Publisher {

    private final Logger LOGGER = LoggerFactory.getLogger(InvoicePublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;
    private final SlackResponderService slackResponderService;

    @Autowired
    public InvoicePublisher(ApplicationEventPublisher applicationEventPublisher, SlackResponderService slackResponderService) {
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
                String firstCell = XslxProcessorService.getCell(row, "B", evaluator);
                if (firstCell != null) {
                    Invoice invoice = new Invoice(this);
                    invoice.setAgreementNumber(firstCell);
                    invoice.setAgreementIssueDate(XslxProcessorService.getCell(row, "C", evaluator));
                    invoice.setFullNameEng(XslxProcessorService.getCell(row, "D", evaluator));
                    invoice.setFullNameUkr(XslxProcessorService.getCell(row, "E", evaluator));
                    invoice.setAddressEng(XslxProcessorService.getCell(row, "F", evaluator));
                    invoice.setAddressUrk(XslxProcessorService.getCell(row, "G", evaluator));
                    invoice.setIpn(XslxProcessorService.getCell(row, "H", evaluator));
                    invoice.setServiceEng(XslxProcessorService.getCell(row, "I", evaluator));
                    invoice.setServiceUkr(XslxProcessorService.getCell(row, "J", evaluator));
                    invoice.setPrice(XslxProcessorService.getCell(row, "K", evaluator));
                    invoice.setAccountNumberUsd(XslxProcessorService.getCell(row, "L", evaluator));
                    invoice.setBankNameEng(XslxProcessorService.getCell(row, "M", evaluator));
                    invoice.setBankAddress(XslxProcessorService.getCell(row, "N", evaluator));
                    invoice.setSwiftNumber(XslxProcessorService.getCell(row, "O", evaluator));
                    invoice.setUserEmail(XslxProcessorService.getCell(row, "P", evaluator));
                    invoice.setAuthorSlackId(fileMetaInfo.getAuthor());
                    LOGGER.info("Payslip: {}", invoice);
                    applicationEventPublisher.publishEvent(invoice);
                }
            } catch (UnsupportedOperationException e) {
                slackResponderService.sendErrorMessageToInitiator(fileMetaInfo.getAuthor(),
                        "Error during processing", e.getMessage());
            }
        }
    }
}
