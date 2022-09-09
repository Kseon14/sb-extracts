package com.am.sbextracts.publisher;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.vo.FileMetaInfo;
import com.am.sbextracts.vo.TaxPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import static com.am.sbextracts.listener.ListenerUtils.getEmails;
import static com.am.sbextracts.publisher.PublisherFactory.Type.TAX_PAYMENT_WITH_EMAL;
import static com.am.sbextracts.publisher.PublisherFactory.Type.getByFileName;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaxPaymentPublisher implements Publisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @SbExceptionHandler
    public void produce(XSSFWorkbook workbook, FileMetaInfo fileMetaInfo) {
        XSSFSheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        PublisherFactory.Type type = getByFileName(fileMetaInfo.getName());

        try {
            XlsxUtil.validateFile(PublisherFactory.Type.TAX_PAYMENT, workbook);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }

                String firstCell = XlsxUtil.getCell(row, "A", evaluator);
                if (firstCell != null) {
                    TaxPayment taxPayment = new TaxPayment(this);
                    taxPayment.setTaxCode(firstCell);
                    taxPayment.setFullName(XlsxUtil.getCell(row, "B", evaluator));
                    taxPayment.setAmount(XlsxUtil.getCell(row, "C", evaluator));
                    taxPayment.setReceiver(XlsxUtil.getCell(row, "D", evaluator));
                    taxPayment.setAccount(XlsxUtil.getCell(row, "E", evaluator));
                    taxPayment.setCode(XlsxUtil.getCell(row, "F", evaluator));
                    taxPayment.setPurposeOfPayment(XlsxUtil.getCell(row, "G", evaluator));
                    taxPayment.setUserEmail(XlsxUtil.getCell(row, "H", evaluator));
                    taxPayment.setDueDate(XlsxUtil.getDateFromCell(row, "I"));
                    taxPayment.setTaxType(XlsxUtil.getCell(row, "J", evaluator));
                    taxPayment.setAuthorSlackId(fileMetaInfo.getAuthor());
                    taxPayment.setWithEmail(TAX_PAYMENT_WITH_EMAL.equals(type));
                    if (taxPayment.isWithEmail()) {
                        taxPayment.setAdditionalUserEmail(getEmails("K", evaluator, row));
                    }
                    log.info("Tax payment: {}", taxPayment);
                    applicationEventPublisher.publishEvent(taxPayment);
                }
            }
        } catch (UnsupportedOperationException e) {
            throw new SbExtractsException("Error during processing", e, fileMetaInfo.getAuthor());
        }
    }
}
