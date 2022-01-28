package com.am.sbextracts.service;

import com.am.sbextracts.DeleteOnCloseFileInputStream;
import com.am.sbextracts.vo.SlackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackFileDownloader implements FileDownloader {
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private final ProcessorService processorService;
    private final SlackResponderService slackResponderService;

    @Override
    public void downloadFile(List<SlackEvent.FileMetaInfo> fileMetaInfos) {
        if (CollectionUtils.isEmpty(fileMetaInfos)) {
            log.info("fileInfos is empty");
            return;
        }
        executorService.execute(() -> {
            for (SlackEvent.FileMetaInfo fileMetaInfo : fileMetaInfos) {

                if (StringUtils.isEmpty(fileMetaInfo.getId())) {
                    log.info("fileInfo.getUrlPrivate() is empty");
                    continue;
                }

                com.slack.api.model.File slackFile;
                try {
                    slackFile = slackResponderService.getFile(fileMetaInfo);
                } catch (Exception e) {
                    log.error("fileInfo.getUrlPrivate() is empty", e);
                    continue;
                }

                if (!"xlsx".equals(slackFile.getFiletype())) {
                    log.info("file not xlsx");
                    continue;
                }
                String fileName = UUID.randomUUID().toString();
                slackResponderService.downloadFile(fileName, slackFile);
                try {
                    processorService.process(new DeleteOnCloseFileInputStream(fileName), fileMetaInfo);
                } catch (IOException e) {
                    log.error("Error during processing", e);
                }
            }
        });
    }
}
