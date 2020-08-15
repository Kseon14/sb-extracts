package com.am.sbextracts.producer;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.service.XslxProcessorService;
import com.am.sbextracts.vo.Payslip;
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
public class PayslipPublisher implements Publisher {

    private final Logger LOGGER = LoggerFactory.getLogger(PayslipPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public PayslipPublisher(ApplicationEventPublisher applicationEventPublisher) {
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
                String firstCell = XslxProcessorService.getCell(row, "A", evaluator);
                if (firstCell != null) {
                    Payslip payslip = new Payslip(this);
                    payslip.setFullName(firstCell);
                    payslip.setContractRate(XslxProcessorService.getCell(row, "B", evaluator));
                    payslip.setOtherIncome(XslxProcessorService.getCell(row, "C", evaluator));
                    payslip.setSocialTax(XslxProcessorService.getCell(row, "D", evaluator));
                    payslip.setInsurance(XslxProcessorService.getCell(row, "E", evaluator));
                    payslip.setRent(XslxProcessorService.getCell(row, "F", evaluator));
                    payslip.setCurrencyRate(XslxProcessorService.getCell(row, "G", evaluator));
                    payslip.setTotalNet(XslxProcessorService.getCell(row, "H", evaluator));
                    payslip.setCurrentPaymentTax(XslxProcessorService.getCell(row, "I", evaluator));
                    payslip.setTotalGross(XslxProcessorService.getCell(row, "J", evaluator));
                    payslip.setUserEmail(XslxProcessorService.getCell(row, "K", evaluator));
                    payslip.setAuthorSlackId(fileMetaInfo.getAuthor());
                    LOGGER.info("Payslip: {}", payslip);
                    applicationEventPublisher.publishEvent(payslip);
                }
            } catch (UnsupportedOperationException e) {
                throw new SbExtractsException("Error during processing", e, "not known yet",  fileMetaInfo.getAuthor());
            }
        }
    }
}
