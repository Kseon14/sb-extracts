package com.am.sbextracts.service.integration;

import com.am.sbextracts.model.InternalSlackEventResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProcessMarkupService implements Process {
    @Override
    public void process(InternalSlackEventResponse slackEventResponse) {
        log.info("working");
    }
}
