package com.am.sbextracts.controller;

import com.am.sbextracts.service.ProcessorService;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/files")
public class InputFileController {

    private final static Logger LOGGER = LoggerFactory.getLogger(InputFileController.class);

    @Value("${slack.verification.token}")
    private String verificationToken;

    private final ProcessorService processorService;

    @Autowired
    public InputFileController(final ProcessorService processorService) {
        this.processorService = processorService;
    }

    @PostMapping
    public SlackResponse handleFile(@RequestHeader("slack-token") String token,
                                    MultipartFile file) throws IOException {
        if (!verificationToken.equals(token)) {
            throw new IllegalArgumentException();
        }
        if (file == null) {
            LOGGER.info("file is null");
            return new SlackResponse("file is null");
        }
        LOGGER.info("fileName : {}", file.getName());
        processorService.process(file.getInputStream(), new SlackEvent.FileMetaInfo());
        return new SlackResponse("done");
    }
}
