package com.am.sbextracts.controller;

import com.am.sbextracts.service.FileDownloader;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.am.sbextracts.vo.SlackEvent.Event.Type.FILE_SHARE;
import static com.am.sbextracts.vo.SlackEvent.Event.Type.MESSAGE;
import static com.am.sbextracts.vo.SlackEvent.Type.EVENT_CALLBACK;
import static com.am.sbextracts.vo.SlackEvent.Type.URL_VERIFICATION;

@RestController
@RequestMapping("/api/events")
public class SlackEventController {

    private final static Logger LOGGER = LoggerFactory.getLogger(SlackEventController.class);

    private final FileDownloader downloader;
    @Value("${slack.verification.token}")
    private String verificationToken;

    @Autowired
    public SlackEventController(final FileDownloader downloader) {
        this.downloader = downloader;
    }

    @PostMapping
    public Object eventHandler(@RequestBody SlackEvent slackEvent) {

        LOGGER.info("Request content {}", slackEvent);
        if (isTokenValid.test(slackEvent.getToken())) {
            throw new IllegalArgumentException();
        }
        if (URL_VERIFICATION == slackEvent.getType()) {
            return slackEvent.getChallenge();
        }

        Consumer<SlackEvent> slackEventConsumer = event -> downloader.downloadFile(event.getEvent().getFileMetaInfos());

        if (isEventCallback.and(isFileShare).test(slackEvent)) {
            slackEventConsumer.accept(slackEvent);
        }
        return null;
    }

    @PostMapping("ping")
    public SlackResponse ping() {
        return new SlackResponse("I'm here");
    }

    private final Predicate<String> isTokenValid = token -> !token.equals(verificationToken);
    private final Predicate<SlackEvent> isEventCallback = event -> EVENT_CALLBACK == event.getType();
    private final Predicate<SlackEvent> isFileShare = event -> MESSAGE == event.getEvent().getType() && FILE_SHARE == event.getEvent().getSubtype();

}
