package com.am.sbextracts.publisher;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.format.CellGeneralFormatter;
import org.apache.poi.ss.format.CellNumberFormatter;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Date;
import java.util.DuplicateFormatFlagsException;
import java.util.List;
import java.util.Locale;

public final class XlsxUtil {

    private XlsxUtil() {
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
                    return "0.0";
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
        try {
            return cell.getDateCellValue();
        } catch (IllegalStateException e) {
            throw new UnsupportedOperationException(String.format("in [%s], cell type not supported %s",
                    cell.getAddress(),
                    cell.getCellStyle().getDataFormatString()));
        }
    }

    public static void validateFile(PublisherFactory.Type type, XSSFWorkbook workbook) {
        InputStream inputStream = XlsxUtil.class.getClassLoader().getResourceAsStream("column-config/"
                + getFileName(type) + ".yaml");
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        ColumnConfig columnConfig = new Yaml().loadAs(inputStream,
                ColumnConfig.class);

        List<String> configCell = columnConfig.getCell();
        List<String> dateCell = columnConfig.getDateCell();

        for (Row row : workbook.getSheetAt(0)) {
            if (row.getRowNum() == 0) {
                continue;
            }
            if (CollectionUtils.isNotEmpty(configCell)) {
                if (getCell(row, configCell.get(0), evaluator) != null) {
                    for(String column : configCell) {
                        if (StringUtils.isBlank(getCell(row, column, evaluator))){
                            throw new UnsupportedOperationException(String.format("in [%s%s] cell is Empty",
                                    column, row.getRowNum() + 1));
                        }
                    }
                }
            }
            if (CollectionUtils.isNotEmpty(dateCell)) {
                if (getDateFromCell(row, dateCell.get(0)) != null) {
                    for (String column : dateCell) {
                        if (getDateFromCell(row, column) == null) {
                            throw new UnsupportedOperationException(String.format("in [%s%s] cell is Empty",
                                    column, row.getRowNum() + 1));
                        }
                    }
                }
            }
        }
    }

    private static String getFileName(PublisherFactory.Type type) {
        return type.name().toLowerCase(Locale.ENGLISH).replace("_", "-");
    }
}
