package com.am.sbextracts.producer;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
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
    private final static Logger LOGGER = LoggerFactory.getLogger(TaxPaymentPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public TaxPaymentPublisher(ApplicationEventPublisher applicationEventPublisher) {
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
            String firstCell = XlsxUtil.getCell(row, "A", evaluator);
            if (firstCell != null) {
                TaxPayment taxPayment = new TaxPayment(this);
                taxPayment.setTaxCode(firstCell);
                taxPayment.setFullName(XlsxUtil.getCell(row, "B", evaluator));
                taxPayment.setAmount(XlsxUtil.getCell(row, "C", evaluator));
                taxPayment.setBankName(XlsxUtil.getCell(row, "D", evaluator));
                taxPayment.setMfo(XlsxUtil.getCell(row, "E", evaluator));
                taxPayment.setReceiver(XlsxUtil.getCell(row, "F", evaluator));
                taxPayment.setAccount(XlsxUtil.getCell(row, "G", evaluator));
                taxPayment.setCode(XlsxUtil.getCell(row, "H", evaluator));
                taxPayment.setPurposeOfPayment(XlsxUtil.getCell(row, "I", evaluator));
                taxPayment.setUserEmail(XlsxUtil.getCell(row, "J", evaluator));
                taxPayment.setDueDate(XlsxUtil.getDateFromCell(row, "K"));
                taxPayment.setTaxType(XlsxUtil.getCell(row, "L", evaluator));
                taxPayment.setAuthorSlackId(fileMetaInfo.getAuthor());
                LOGGER.info("Tax payment: {}", taxPayment);
                applicationEventPublisher.publishEvent(taxPayment);
            }
            } catch (UnsupportedOperationException e) {
                throw new SbExtractsException("Error during processing", e, "not known yet", fileMetaInfo.getAuthor());
            }
        }
    }
}
