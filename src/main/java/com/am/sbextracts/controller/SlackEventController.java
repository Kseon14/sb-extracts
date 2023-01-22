package com.am.sbextracts.controller;

import com.am.sbextracts.model.SlackSlashCommandRequest;
import com.am.sbextracts.publisher.PublisherFactory;
import com.am.sbextracts.service.FileDownloader;
import com.am.sbextracts.service.integration.GAuthService;
import com.am.sbextracts.vo.FileMetaInfo;
import com.am.sbextracts.vo.SlackEvent;
import com.am.sbextracts.vo.SlackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.am.sbextracts.vo.Event.Type.FILE_SHARE;
import static com.am.sbextracts.vo.Event.Type.MESSAGE;
import static com.am.sbextracts.vo.SlackEvent.Type.EVENT_CALLBACK;
import static com.am.sbextracts.vo.SlackEvent.Type.URL_VERIFICATION;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class SlackEventController {

    private static final Predicate<SlackEvent> IS_EVENT_CALLBACK = event -> EVENT_CALLBACK == event.getType();
    private static final Predicate<SlackEvent> IS_FILE_SHARE = event -> MESSAGE == event.getEvent().getType()
            && FILE_SHARE == event.getEvent().getSubtype();
    private static final Predicate<SlackEvent> IS_XLSX = event -> "xlsx".equals(event.getEvent()
            .getFileMetaInfos().get(0).getFileType());
    private static final Set<String> processedFiles = ConcurrentHashMap.newKeySet();

    private final FileDownloader downloader;

    private final GAuthService gAuthService;
    @Value("${slack.verification.token}")
    private String verificationToken;

    @Value("#{${slack.allowedUsers}}")
    private final List<String> allowedUsers;

    private final Predicate<String> isTokenValid = token -> !token.equals(verificationToken);

    @PostMapping
    public Object eventHandler(@RequestBody SlackEvent slackEvent) {

        log.debug("Request content {}", slackEvent);
        if (isTokenValid.test(slackEvent.getToken())) {
            throw new IllegalArgumentException();
        }
        if (URL_VERIFICATION == slackEvent.getType()) {
            return slackEvent.getChallenge();
        }
        if (IS_EVENT_CALLBACK.and(IS_FILE_SHARE).negate().test(slackEvent) ||
                CollectionUtils.isEmpty(slackEvent.getEvent().getFileMetaInfos()) ||
                IS_XLSX.negate().test(slackEvent)) {
            log.trace("{} skipping....", slackEvent);
            return ResponseEntity.ok().build();
        }

        Consumer<SlackEvent> slackEventConsumer = event -> downloader.downloadFile(event.getEvent().getFileMetaInfos());
        FileMetaInfo fileMetaInfo = slackEvent.getEvent().getFileMetaInfos().get(0);
        if (processedFiles.contains(fileMetaInfo.getId())){
            log.info("already processed file {}", fileMetaInfo);
            return new SlackResponse("already processed file {}");
        }
        processedFiles.add(fileMetaInfo.getId());
        slackEventConsumer.accept(slackEvent);
        return  ResponseEntity.ok().build();
    }

    @PostMapping("ping")
    public SlackResponse ping() {
        return new SlackResponse("I'm here");
    }

    @PostMapping("file_types")
    public SlackResponse getFileTypeInfo() {
        return new SlackResponse(Arrays.toString(PublisherFactory.Type.values()));
    }

    @PostMapping("reauth")
    public ResponseEntity<Object> reAuth(SlackSlashCommandRequest slackSlashCommandRequest) {
        if (isNotAllowedUser(slackSlashCommandRequest)) {
            return ResponseEntity.ok().build();
        }
        Executors.newSingleThreadExecutor().execute(() -> gAuthService.reAuth(slackSlashCommandRequest));
        return ResponseEntity.ok().build();
    }

    private boolean isNotAllowedUser(SlackSlashCommandRequest slackSlashCommandRequest) {
        return !allowedUsers.contains(slackSlashCommandRequest.getUser_id());
    }

}
