package com.am.sbextracts.service.integration;

import com.am.sbextracts.model.InternalSlackEventResponse;

public interface Process {

    void process(InternalSlackEventResponse slackEventResponse);
}
