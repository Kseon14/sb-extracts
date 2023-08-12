package com.am.sbextracts.controller;

import com.am.sbextracts.publisher.PublisherFactory;
import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.service.integration.ProcessingFactory;
import com.am.sbextracts.vo.Payload;
import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.am.sbextracts.vo.SlackResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static com.am.sbextracts.vo.SlackInteractiveEvent.Type.BLOCK_ACTIONS;
import static com.am.sbextracts.vo.SlackInteractiveEvent.Type.VIEW_SUBMISSION;

@Slf4j
@RestController
@RequestMapping("/api/interactivity")
@RequiredArgsConstructor
public class SlackInteractivityController {

    private final static Predicate<SlackInteractiveEvent> IS_VALID_EVENT =
            event -> VIEW_SUBMISSION == event.getType() || BLOCK_ACTIONS == event.getType();

    private final ResponderService slackResponderService;
    private final ProcessingFactory processingFactory;
    private final ObjectMapper objectMapper;

    @Value("${VERIFICATION_TOKEN}")
    private String verificationToken;
    private final Predicate<String> isTokenValid = token -> !token.equals(verificationToken);

    @Value("#{${ALLOWED_USER}}")
    private final List<String> allowedUsers;

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Object eventHandler(Payload payload) {

        SlackInteractiveEvent slackInteractiveEvent;
        try {
            slackInteractiveEvent = objectMapper.readValue(payload.getPayload(),
                    SlackInteractiveEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        log.info("Request content {}", slackInteractiveEvent);
        if (isTokenValid.test(slackInteractiveEvent.getToken())) {
            throw new IllegalArgumentException();
        }
        if (IS_VALID_EVENT.negate().test(slackInteractiveEvent)) {
            throw new IllegalArgumentException();
        }

        if (slackInteractiveEvent.getType() == VIEW_SUBMISSION) {
            processingFactory.startProcessing(slackInteractiveEvent);
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "process/markup", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> markup(@SIE SlackInteractiveEvent request) {
        if (isNotAllowedUser(request)) {
            return ResponseEntity.ok().build();
        }
        slackResponderService.sendMarkupView(request);
        return ResponseEntity.ok().build();
    }

//    @PostMapping(value = "debtors", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
//    public ResponseEntity<Void> getDebtors(@SIE SlackInteractiveEvent request) {
//        if (isNotAllowedUser(request)) {
//            return ResponseEntity.ok().build();
//        }
//        slackResponderService.sendDebtors(request);
//        return ResponseEntity.ok().build();
//    }

    @PostMapping(value = "process/signed", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> downloadSigned(@SIE SlackInteractiveEvent request) {
        if (isNotAllowedUser(request)) {
            return ResponseEntity.ok().build();
        }
        slackResponderService.sendDownloadSigned(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "process/invoice", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> downloadInvoice(@SIE SlackInteractiveEvent request) {
        if (isNotAllowedUser(request)) {
            return ResponseEntity.ok().build();
        }
        slackResponderService.sendDownloadInvoice(request);
        return ResponseEntity.ok().build();
    }

//    @PostMapping(value = "debtors/push", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
//    public ResponseEntity<Void> pushDebtors(@SIE SlackInteractiveEvent request) {
//        if (isNotAllowedUser(request)) {
//            return ResponseEntity.ok().build();
//        }
//        slackResponderService.pushDebtors(request);
//        return ResponseEntity.ok().build();
//    }

    @PostMapping("file_types")
    public SlackResponse getFileTypeInfo() {
        return new SlackResponse(Arrays.toString(PublisherFactory.Type.values()));
    }

    private boolean isNotAllowedUser(SlackInteractiveEvent slackInteractiveEvent) {
        return !allowedUsers.contains(slackInteractiveEvent.getUserId());
    }
}
