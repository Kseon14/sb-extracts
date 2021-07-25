package com.am.sbextracts.publisher;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.vo.Payslip;
import com.am.sbextracts.vo.SlackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayslipPublisher implements Publisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @SbExceptionHandler
    public void produce(XSSFWorkbook workbook, SlackEvent.FileMetaInfo fileMetaInfo) {
        XSSFSheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        try {
            XlsxUtil.validateFile(PublisherFactory.Type.PAYSLIP, workbook);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }

                String firstCell = XlsxUtil.getCell(row, "A", evaluator);
                if (firstCell != null) {
                    Payslip payslip = new Payslip(this);
                    payslip.setFullName(firstCell);
                    payslip.setContractRate(XlsxUtil.getCell(row, "B", evaluator));
                    payslip.setOtherIncome(XlsxUtil.getCell(row, "C", evaluator));
                    payslip.setSocialTax(XlsxUtil.getCell(row, "D", evaluator));
                    payslip.setInsurance(XlsxUtil.getCell(row, "E", evaluator));
                    payslip.setRent(XlsxUtil.getCell(row, "F", evaluator));
                    payslip.setCurrencyRate(XlsxUtil.getCell(row, "G", evaluator));
                    payslip.setTotalNet(XlsxUtil.getCell(row, "H", evaluator));
                    payslip.setCurrentPaymentTax(XlsxUtil.getCell(row, "I", evaluator));
                    payslip.setTotalGross(XlsxUtil.getCell(row, "J", evaluator));
                    payslip.setUserEmail(XlsxUtil.getCell(row, "K", evaluator));
                    payslip.setAuthorSlackId(fileMetaInfo.getAuthor());
                    log.info("Payslip: {}", payslip);
                    applicationEventPublisher.publishEvent(payslip);
                }
            }
        } catch (UnsupportedOperationException e) {
            throw new SbExtractsException("Error during processing", e, fileMetaInfo.getAuthor());
        }
    }
}
