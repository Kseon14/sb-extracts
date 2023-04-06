package com.am.sbextracts.controller;

import com.am.sbextracts.service.ProcessorService;
import com.am.sbextracts.vo.FileMetaInfo;
import com.am.sbextracts.vo.SlackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class InputFileController {

    @Value("${VERIFICATION_TOKEN}")
    private final String verificationToken;

    private final ProcessorService processorService;

    @PostMapping
    public SlackResponse handleFile(@RequestHeader("slack-token") String token,
                                    MultipartFile file) throws IOException {
        if (!verificationToken.equals(token)) {
            throw new IllegalArgumentException();
        }
        if (file == null) {
            log.info("file is null");
            return new SlackResponse("file is null");
        }
        log.info("fileName : {}", file.getName());
        processorService.process(file.getInputStream(), new FileMetaInfo());
        return new SlackResponse("done");
    }
}
