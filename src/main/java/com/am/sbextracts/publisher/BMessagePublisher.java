package com.am.sbextracts.publisher;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.vo.BMessage;
import com.am.sbextracts.vo.SlackEvent;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;

@Component
public class BMessagePublisher implements Publisher {

    private final static Logger LOGGER = LoggerFactory.getLogger(BMessagePublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public BMessagePublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @SbExceptionHandler
    public void produce(XSSFWorkbook workbook, SlackEvent.FileMetaInfo fileMetaInfo) {
        XSSFSheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        Date date = null;
        String text = null;
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            try {
                String firstCell = XlsxUtil.getCell(row, "A", evaluator);
                if (firstCell != null) {
                    if (row.getRowNum() == 1) {
                        text = XlsxUtil.getCell(row, "C", evaluator);
                        date = XlsxUtil.getDateFromCell(row, "D");
                        if (StringUtils.isBlank(text) || Objects.isNull(date)){
                            throw new SbExtractsException("message or date are empty", "not known yet", fileMetaInfo.getAuthor());
                        }
                    }
                    BMessage message = new BMessage(this);
                    message.setFullName(firstCell);
                    message.setUserEmail(XlsxUtil.getCell(row, "B", evaluator));
                    message.setDueDate(date);
                    message.setText(text);

                    message.setAuthorSlackId(fileMetaInfo.getAuthor());
                    LOGGER.info("Invoice: {}", message);
                    applicationEventPublisher.publishEvent(message);
                }
            } catch (UnsupportedOperationException e) {
                throw new SbExtractsException("Error during processing", e, "not known yet", fileMetaInfo.getAuthor());

            }
        }
    }
}
