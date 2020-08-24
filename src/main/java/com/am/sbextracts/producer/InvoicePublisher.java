package com.am.sbextracts.producer;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
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

    @Autowired
    public InvoicePublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @SbExceptionHandler
    public void produce(XSSFWorkbook workbook, SlackEvent.FileMetaInfo fileMetaInfo) {
        XSSFSheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            try {
                String firstCell = XlsxUtil.getCell(row, "B", evaluator);
                if (firstCell != null) {
                    Invoice invoice = new Invoice(this);
                    invoice.setAgreementNumber(firstCell);
                    invoice.setAgreementIssueDate(XlsxUtil.getDateFromCell(row, "C"));
                    invoice.setFullNameEng(XlsxUtil.getCell(row, "D", evaluator));
                    invoice.setFullNameUkr(XlsxUtil.getCell(row, "E", evaluator));
                    invoice.setAddressEng(XlsxUtil.getCell(row, "F", evaluator));
                    invoice.setAddressUrk(XlsxUtil.getCell(row, "G", evaluator));
                    invoice.setIpn(XlsxUtil.getCell(row, "H", evaluator));
                    invoice.setServiceEng(XlsxUtil.getCell(row, "I", evaluator));
                    invoice.setServiceUkr(XlsxUtil.getCell(row, "J", evaluator));
                    invoice.setPrice(XlsxUtil.getCell(row, "K", evaluator));
                    invoice.setAccountNumberUsd(XlsxUtil.getCell(row, "L", evaluator));
                    invoice.setBankNameEng(XlsxUtil.getCell(row, "M", evaluator));
                    invoice.setBankAddress(XlsxUtil.getCell(row, "N", evaluator));
                    invoice.setSwiftNumber(XlsxUtil.getCell(row, "O", evaluator));
                    invoice.setUserEmail(XlsxUtil.getCell(row, "P", evaluator));
                    invoice.setAuthorSlackId(fileMetaInfo.getAuthor());
                    LOGGER.info("Invoice: {}", invoice);
                    applicationEventPublisher.publishEvent(invoice);
                }
            } catch (UnsupportedOperationException e) {
                throw new SbExtractsException("Error during processing", e, "not known yet", fileMetaInfo.getAuthor());

            }
        }
    }
}
