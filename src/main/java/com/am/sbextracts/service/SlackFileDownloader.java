package com.am.sbextracts.service;

import com.am.sbextracts.DeleteOnCloseFileInputStream;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackFileInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SlackFileDownloader implements FileDownloader {

    private final Logger LOGGER = LoggerFactory.getLogger(SlackFileDownloader.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private final ProcessorService processorService;
    private final SlackResponderService slackResponderService;

    @Autowired
    public SlackFileDownloader(ProcessorService processorService, SlackResponderService slackResponderService) {
        this.processorService = processorService;
        this.slackResponderService = slackResponderService;
    }

    @Override
    public void downloadFile(List<SlackEvent.FileMetaInfo> fileMetaInfos) {
        if (CollectionUtils.isEmpty(fileMetaInfos)) {
            LOGGER.info("fileInfos is empty");
            return;
        }
        executorService.execute(() -> {
            for (SlackEvent.FileMetaInfo fileMetaInfo : fileMetaInfos) {

                if (StringUtils.isEmpty(fileMetaInfo.getId())) {
                    LOGGER.info("fileInfo.getUrlPrivate() is empty");
                    continue;
                }

                SlackFileInfo slackFile;
                try {
                    slackFile = slackResponderService.getFileInfo(fileMetaInfo);
                } catch (ExecutionException | InterruptedException | JsonProcessingException e) {
                    LOGGER.error("fileInfo.getUrlPrivate() is empty", e);
                    continue;
                }

                if (!"xlsx".equals(slackFile.getFileMetaInfo().getFileType())) {
                    LOGGER.info("file not xlsx");
                    continue;
                }
                String fileName = UUID.randomUUID().toString();
                try {
                    slackResponderService.downloadFile(fileName, slackFile);
                } catch (ExecutionException | InterruptedException e) {
                    LOGGER.error("Error during file download", e);
                    continue;
                }

                try {
                    processorService.process(new DeleteOnCloseFileInputStream(fileName), fileMetaInfo);
                } catch (IOException e) {
                    LOGGER.error("Error during processing", e);
                }
            }
        });
    }
}
