package com.am.sbextracts.producer;

import org.apache.poi.ss.format.CellGeneralFormatter;
import org.apache.poi.ss.format.CellNumberFormatter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;

import java.util.Date;
import java.util.DuplicateFormatFlagsException;
import java.util.Locale;

public final class XlsxUtil {

    private XlsxUtil(){
    }

    public static String getCell(Row row, String reference, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(CellReference.convertColStringToIndex(reference));
        if (cell == null || cell.getCellType() == CellType.BLANK) {
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
        } else {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    return getFormattedNumericCell(cell);
            }
        }
        throw new UnsupportedOperationException(String.format("in [%s], cell type not supported %s",
                cell.getAddress(),
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
        } catch (DuplicateFormatFlagsException e) {
            //do nothing
        }
        throw new UnsupportedOperationException(String.format("in [%s], cell type not supported %s",
                cell.getAddress(),
                cell.getCellStyle().getDataFormatString()));
    }

    public static Date getDateFromCell(Row row, String reference) {
        Cell cell = row.getCell(CellReference.convertColStringToIndex(reference));
        if (cell == null) {
            return null;
        }
        return cell.getDateCellValue();
    }
}
