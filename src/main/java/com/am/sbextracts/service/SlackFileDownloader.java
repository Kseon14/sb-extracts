package com.am.sbextracts.service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.am.sbextracts.DeleteOnCloseFileInputStream;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackFileInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SlackFileDownloader implements FileDownloader {

    private final Logger LOGGER = LoggerFactory.getLogger(SlackFileDownloader.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    private final Processor processor;

    @Value("${slack.token}")
    private String token;

    @Autowired
    public SlackFileDownloader(Processor processor) {
        this.processor = processor;
    }

    @Override
    public void downloadFile(List<SlackEvent.FileInfo> fileInfos) {
        if (CollectionUtils.isEmpty(fileInfos)) {
            LOGGER.info("fileInfos is empty");
            return;
        }
        executorService.execute(() -> {
            for (SlackEvent.FileInfo fileInfo : fileInfos) {

                if (StringUtils.isEmpty(fileInfo.getId())) {
                    LOGGER.info("fileInfo.getUrlPrivate() is empty");
                    continue;
                }

                AsyncHttpClient client = Dsl.asyncHttpClient();
                SlackFileInfo slackFile;
                try {
                    slackFile = getFileInfo(fileInfo, client);
                } catch (ExecutionException | InterruptedException | JsonProcessingException e) {
                    LOGGER.error("fileInfo.getUrlPrivate() is empty", e);
                    continue;
                }

                if (!"xlsx".equals(slackFile.getFileInfo().getFileType())) {
                    LOGGER.info("file not xlsx");
                    continue;
                }

                Request request = getGETBuilder().setUrl(slackFile.getFileInfo().getUrlPrivate())
                        .build();
                FileOutputStream stream;
                String fileName = UUID.randomUUID().toString();
                try {
                    stream = new FileOutputStream(fileName);
                } catch (FileNotFoundException e) {
                    LOGGER.error("Error during fileSaving", e);
                    return;
                }

                ListenableFuture<FileOutputStream> responseListenableFuture = client.executeRequest(request,
                        new AsyncCompletionHandler<FileOutputStream>() {

                            @Override
                            public State onBodyPartReceived(HttpResponseBodyPart bodyPart)
                                    throws Exception {
                                stream.getChannel().write(bodyPart.getBodyByteBuffer());
                                return State.CONTINUE;
                            }

                            @Override
                            public FileOutputStream onCompleted(Response response) {
                                return stream;
                            }
                        });

                try {
                    responseListenableFuture.get();
                    LOGGER.info("File downloaded");
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.error("Error during fileSaving", e);
                    return;
                }
                try {
                    stream.close();
                } catch (IOException e) {
                    LOGGER.error("Error during close", e);
                    return;
                }
                try {
                    processor.process(new DeleteOnCloseFileInputStream(fileName), fileInfo.getAuthor());
                } catch (IOException e) {
                    LOGGER.error("Error during processing", e);
                    return;
                }
            }
        });
    }

    private SlackFileInfo getFileInfo(SlackEvent.FileInfo fileInfo, AsyncHttpClient client)
            throws ExecutionException, InterruptedException, JsonProcessingException {

        Request request = getGETBuilder().setUrl("https://slack.com/api/files.info")
                .addQueryParam("file", fileInfo.getId())
                .build();
        Future<Response> responseFuture = client.executeRequest(request);
        Response response;
        response = responseFuture.get();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(response.getResponseBody(), SlackFileInfo.class);
    }

    private RequestBuilder getGETBuilder() {
        return new RequestBuilder("GET").addHeader("Authorization", "Bearer " + token);
    }
}
