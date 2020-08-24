package com.am.sbextracts.service;

import com.am.sbextracts.producer.ProducerFactory;
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
    private final ProducerFactory producerFactory;

    public XlsxProcessorService(ProducerFactory producerFactory) {
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

}
