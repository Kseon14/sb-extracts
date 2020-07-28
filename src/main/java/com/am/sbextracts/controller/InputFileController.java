package com.am.sbextracts.controller;

import java.io.IOException;
import org.slf4j.Logger;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.am.sbextracts.service.Processor;

@RestController
@RequestMapping("/api/files")
public class InputFileController {

    private final Logger LOGGER = LoggerFactory.getLogger(InputFileController.class);

    private final Processor processor;

    @Autowired
    public InputFileController(final Processor processor) {
        this.processor = processor;
    }

    @PostMapping
    public void handleFile(MultipartFile file) throws IOException {
        if (file == null) {
            return;
        }
        LOGGER.info("fileName : {}", file.getName());
        processor.process(file.getInputStream());
    }
}
