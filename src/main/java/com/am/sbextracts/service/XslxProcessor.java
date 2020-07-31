package com.am.sbextracts.service;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.am.sbextracts.vo.Person;
import com.am.sbextracts.vo.SlackResponse;

@Service
public class XslxProcessor implements Processor {

    private final Logger LOGGER = LoggerFactory.getLogger(XslxProcessor.class);
    private final Responder responder;

    public XslxProcessor(Responder responder) {
        this.responder = responder;
    }

    @Override
    public SlackResponse process(InputStream inputStream, String author) throws IOException {
        LOGGER.info("File start processing");
        List<Person> personList = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                Person person = new Person();
                String firstCell = getCell(row, "A", evaluator);
                if (firstCell != null) {
                    person.setTaxCode(firstCell);
                    person.setFullName(getCell(row, "B", evaluator));
                    person.setAmount(getCell(row, "C", evaluator));
                    person.setBankName(getCell(row, "D", evaluator));
                    person.setMfo(getCell(row, "E", evaluator));
                    person.setReceiver(getCell(row, "F", evaluator));
                    person.setAccount(getCell(row, "G", evaluator));
                    person.setCode(getCell(row, "H", evaluator));
                    person.setPurposeOfPayment(getCell(row, "I", evaluator));
                    person.setUserName(getCell(row, "J", evaluator));
                    person.setDueDate(getCell(row, "K", evaluator));
                    person.setTaxType(getCell(row, "L", evaluator));
                    person.setAuthor(author);
                    LOGGER.info("Person: {}", person);
                    personList.add(person);
                }
            }
        } finally {
            inputStream.close();
        }

        responder.respond(personList);
        SlackResponse slackResponse = new SlackResponse();
        slackResponse.setText(personList.stream().map(Person::getUserName).collect(Collectors.joining(",")));
        return slackResponse;
    }

    private String getCell(Row row, String reference, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(CellReference.convertColStringToIndex(reference));
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            switch (evaluator.evaluateFormulaCell(cell)) {
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case NUMERIC:
                    return String.valueOf(cell.getNumericCellValue());
                case STRING:
                    return String.valueOf(cell.getStringCellValue());
            }
        } else {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (cell.getCellStyle().getDataFormat() > 0) {
                        return cell.toString();
                    }
                    DecimalFormat format = new DecimalFormat("0.##");
                    return format.format(cell.getNumericCellValue());
            }
        }
        return null;
    }
}
