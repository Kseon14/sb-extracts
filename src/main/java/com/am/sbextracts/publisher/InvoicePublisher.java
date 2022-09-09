package com.am.sbextracts.publisher;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.vo.FileMetaInfo;
import com.am.sbextracts.vo.Invoice;
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
public class InvoicePublisher implements Publisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @SbExceptionHandler
    public void produce(XSSFWorkbook workbook, FileMetaInfo fileMetaInfo) {
        XSSFSheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        try {
            XlsxUtil.validateFile(PublisherFactory.Type.INVOICE, workbook);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }

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
                    log.info("Invoice: {}", invoice);
                    applicationEventPublisher.publishEvent(invoice);
                }
            }
        } catch (UnsupportedOperationException e) {
            throw new SbExtractsException("Error during processing", e, fileMetaInfo.getAuthor());
        }
    }
}
