package com.am.sbextracts.service.integration;

import com.am.sbextracts.pool.SlackClientPool;
import com.slack.api.methods.AsyncMethodsClient;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;

@Slf4j
@Data
public class SlackClientWrapper implements Closeable {

    private final SlackClientPool slackClientPool;
    private final AsyncMethodsClient client;

    @SneakyThrows
    public SlackClientWrapper(SlackClientPool slackClientPool){
        this.slackClientPool = slackClientPool;
        client = this.slackClientPool.borrowObject();
        log.debug("slack Client {}", client);
    }

    @Override
    public void close() {
        if (client != null) {
            log.debug("returning slack Client {} into pool", client);
            slackClientPool.returnObject(client);
        }
    }
}
