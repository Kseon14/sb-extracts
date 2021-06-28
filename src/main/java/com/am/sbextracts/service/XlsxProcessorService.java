package com.am.sbextracts.service;

import com.am.sbextracts.publisher.PublisherFactory;
import com.am.sbextracts.vo.SlackEvent;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class XlsxProcessorService implements ProcessorService {

    private final static Logger LOGGER = LoggerFactory.getLogger(XlsxProcessorService.class);
    private final PublisherFactory publisherFactory;

    public XlsxProcessorService(PublisherFactory publisherFactory) {
        this.publisherFactory = publisherFactory;
    }

    @Override
    public void process(InputStream inputStream, SlackEvent.FileMetaInfo fileMetaInfo) throws IOException {
        LOGGER.info("File start processing");
        try (inputStream; XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            publisherFactory.getProducer(fileMetaInfo).produce(workbook, fileMetaInfo);
        }
    }

}
