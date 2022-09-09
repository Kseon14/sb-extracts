package com.am.sbextracts.publisher;

import com.am.sbextracts.vo.FileMetaInfo;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public interface Publisher {

    void produce(XSSFWorkbook workbook, FileMetaInfo fileMetaInfo);
}
