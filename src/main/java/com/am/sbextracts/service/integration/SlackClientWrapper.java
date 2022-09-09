package com.am.sbextracts.service.integration;

import com.am.sbextracts.pool.SlackClientPool;
import com.slack.api.methods.MethodsClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;

@Slf4j
@Data
public class SlackClientWrapper implements Closeable {

    private final SlackClientPool slackClientPool;
    private final MethodsClient client;

    public SlackClientWrapper(SlackClientPool slackClientPool) {
        this.slackClientPool = slackClientPool;
        try {
            client = this.slackClientPool.borrowObject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        log.trace("slack Client {}", client);
    }

    @Override
    public void close() {
        if (client != null) {
            log.trace("returning slack Client {} into pool", client);
            slackClientPool.returnObject(client);
        }
    }
}
