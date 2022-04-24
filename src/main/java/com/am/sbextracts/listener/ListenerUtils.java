package com.am.sbextracts.listener;

import com.am.sbextracts.publisher.XlsxUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class ListenerUtils {

    public static Set<String> getEmails(String columnName, FormulaEvaluator evaluator, Row row) {
        return Optional.ofNullable(XlsxUtil.getCell(row, columnName, evaluator))
                .map(columnContent -> columnContent.split(";"))
                .map(Arrays::asList)
                .map(HashSet::new)
                .orElse(new HashSet<>());
    }

}
