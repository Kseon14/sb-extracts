package com.am.sbextracts.service;

import com.am.sbextracts.publisher.PublisherFactory;
import com.am.sbextracts.vo.FileMetaInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
public class XlsxProcessorService implements ProcessorService {

    private final PublisherFactory publisherFactory;

    public XlsxProcessorService(PublisherFactory publisherFactory) {
        this.publisherFactory = publisherFactory;
    }

    @Override
    public void process(InputStream inputStream, FileMetaInfo fileMetaInfo) throws IOException {
        log.info("File start processing");
        try (inputStream; XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            publisherFactory.getProducer(fileMetaInfo).produce(workbook, fileMetaInfo);
        } catch (final Exception ex) {
            log.error("Failed to process", ex);
        }
    }

}
