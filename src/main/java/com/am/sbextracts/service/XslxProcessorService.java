package com.am.sbextracts.service;

import com.am.sbextracts.producer.ProducerFactory;
import com.am.sbextracts.vo.SlackEvent;
import org.apache.poi.ss.format.CellDateFormatter;
import org.apache.poi.ss.format.CellGeneralFormatter;
import org.apache.poi.ss.format.CellNumberFormatter;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.DuplicateFormatFlagsException;
import java.util.Locale;

@Service
public class XslxProcessorService implements ProcessorService {

    private final Logger LOGGER = LoggerFactory.getLogger(XslxProcessorService.class);
    private final ProducerFactory producerFactory;

    public XslxProcessorService(ProducerFactory producerFactory) {
        this.producerFactory = producerFactory;
    }

    @Override
    public void process(InputStream inputStream, SlackEvent.FileMetaInfo fileMetaInfo) throws IOException {
        LOGGER.info("File start processing");
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            producerFactory.getProducer(fileMetaInfo).produce(workbook, fileMetaInfo);
        } finally {
            inputStream.close();
        }
    }

    public static String getCell(Row row, String reference, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(CellReference.convertColStringToIndex(reference));
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            switch (evaluator.evaluateFormulaCell(cell)) {
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case NUMERIC:
                    return getFormattedNumericCell(cell);
                case STRING:
                    return String.valueOf(cell.getStringCellValue());
            }
        } else if (cell.getCellType() == CellType.BLANK) {
            return null;
        }  else {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    return getFormattedNumericCell(cell);
            }
        }
        throw new UnsupportedOperationException(String.format("WHERE: R[%s]C[%s] cell type not supported %s",
                cell.getRowIndex(), cell.getColumnIndex(),
                cell.getCellStyle().getDataFormatString()));
    }

    private static String getFormattedNumericCell(Cell cell) {
        short dateFormatNumber = cell.getCellStyle().getDataFormat();
        try {
            if (dateFormatNumber >= BuiltinFormats.FIRST_USER_DEFINED_FORMAT_INDEX) {
                CellNumberFormatter cellNumberFormatter =
                        new CellNumberFormatter(cell.getCellStyle().getDataFormatString());
                String simpleValue = cellNumberFormatter.simpleFormat(cell.getNumericCellValue());

                if (Double.parseDouble(simpleValue) == 0.0) {
                    return null;
                }
                return cellNumberFormatter.format(cell.getNumericCellValue());
            }
            if (dateFormatNumber == 0) {
                CellGeneralFormatter cellGeneralFormatter =
                        new CellGeneralFormatter(Locale.US);
                return cellGeneralFormatter.format(cell.getNumericCellValue());
            }
            if (dateFormatNumber >= 14 && dateFormatNumber <= 16) {
                CellDateFormatter cellDateFormatter =
                        new CellDateFormatter(cell.getCellStyle().getDataFormatString());
                return cellDateFormatter.format(cell.getDateCellValue());
            }
        } catch (DuplicateFormatFlagsException e) {
            //do nothing
        }
        throw new UnsupportedOperationException(String.format("WHERE: R[%s]C[%s] cell type not supported %s",
                cell.getRowIndex(), cell.getColumnIndex(),
                cell.getCellStyle().getDataFormatString()));
    }
}
