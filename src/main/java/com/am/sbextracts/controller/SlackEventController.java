package com.am.sbextracts.controller;

import static com.am.sbextracts.vo.SlackEvent.Event.Type.FILE_SHARE;
import static com.am.sbextracts.vo.SlackEvent.Event.Type.MESSAGE;
import static com.am.sbextracts.vo.SlackEvent.Type.EVENT_CALLBACK;
import static com.am.sbextracts.vo.SlackEvent.Type.URL_VERIFICATION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.am.sbextracts.service.FileDownloader;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackResponse;

@RestController
@RequestMapping("/api/events")
public class SlackEventController {

    private final Logger LOGGER = LoggerFactory.getLogger(SlackEventController.class);

    private final FileDownloader downloader;
    @Value("${slack.verification.token}")
    private String verificationToken;

    @Autowired
    public SlackEventController(FileDownloader downloader) {
        this.downloader = downloader;
    }

    @PostMapping
    public Object eventHandler(@RequestBody SlackEvent slackEvent) {

        LOGGER.info("Request content {}", slackEvent);
        if (!slackEvent.getToken().equals(verificationToken)) {
            throw new IllegalArgumentException();
        }
        if (URL_VERIFICATION == slackEvent.getType()) {
            return slackEvent.getChallenge();
        }
        if (EVENT_CALLBACK == slackEvent.getType()) {
            SlackEvent.Event event = slackEvent.getEvent();
            if (MESSAGE == event.getType() && FILE_SHARE == event.getSubtype()) {
                downloader.downloadFile(event.getFileMetaInfos());
            }
        }
        return null;
    }

    @PostMapping("ping")
    public SlackResponse ping() {
        return new SlackResponse("I'm here");
    }
}
