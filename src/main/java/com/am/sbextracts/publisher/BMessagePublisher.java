package com.am.sbextracts.publisher;

import com.am.sbextracts.exception.SbExceptionHandler;
import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.vo.BMessage;
import com.am.sbextracts.vo.FileMetaInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;

import static com.am.sbextracts.listener.ListenerUtils.getEmails;
import static com.am.sbextracts.publisher.PublisherFactory.Type.BROADCAST_MESSAGE_WITH_EMAIL;
import static com.am.sbextracts.publisher.PublisherFactory.Type.getByFileName;

@Slf4j
@Component
@RequiredArgsConstructor
public class BMessagePublisher implements Publisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @SbExceptionHandler
    public void produce(XSSFWorkbook workbook, FileMetaInfo fileMetaInfo) {
        XSSFSheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        Date date = null;
        String text = null;
        try {
            PublisherFactory.Type type = getByFileName(fileMetaInfo.getName());
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }

                String firstCell = XlsxUtil.getCell(row, "A", evaluator);
                if (firstCell != null) {
                    if (row.getRowNum() == 1) {
                        text = XlsxUtil.getCell(row, "C", evaluator);
                        date = XlsxUtil.getDateFromCell(row, "D");
                        if (StringUtils.isBlank(text) || Objects.isNull(date)) {
                            throw new SbExtractsException("message or date is empty", fileMetaInfo.getAuthor());
                        }
                    }
                    BMessage message = new BMessage(this);
                    message.setFullName(firstCell);
                    message.setUserEmail(XlsxUtil.getCell(row, "B", evaluator));
                    message.setDueDate(date);
                    message.setText(text);
                    message.setWithEmail(BROADCAST_MESSAGE_WITH_EMAIL.equals(type));
                    if (message.isWithEmail()) {
                        message.setAdditionalUserEmail(getEmails("E", evaluator, row));
                    }
                    message.setAuthorSlackId(fileMetaInfo.getAuthor());
                    log.info("Broadcast message: {}", message);
                    applicationEventPublisher.publishEvent(message);
                }
            }
        } catch (UnsupportedOperationException e) {
                throw new SbExtractsException("Error during processing", e, fileMetaInfo.getAuthor());
        }
    }
}
