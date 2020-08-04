package com.am.sbextracts.producer;

import com.am.sbextracts.vo.SlackEvent;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public interface Publisher {

    void produce(XSSFWorkbook workbook, SlackEvent.FileMetaInfo fileMetaInfo);
}
