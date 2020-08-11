package com.am.sbextracts.pool;

import com.hubspot.slack.client.SlackClient;
import com.hubspot.slack.client.SlackClientRuntimeConfig;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SlackClientFactory extends BasePooledObjectFactory<SlackClient> {

    @Value("${slack.token}")
    private String token;

    @Override
    public SlackClient create() {
        SlackClientRuntimeConfig runtimeConfig = SlackClientRuntimeConfig.builder()
                .setTokenSupplier(() -> token)
                .build();
        return com.hubspot.slack.client.SlackClientFactory.defaultFactory().build(runtimeConfig);
    }

    @Override
    public PooledObject<SlackClient> wrap(SlackClient slackClient) {
        return new DefaultPooledObject<>(slackClient);
    }

    @Override
    public void destroyObject(final PooledObject<SlackClient> slackClientPooledObject) throws IOException {
        slackClientPooledObject.getObject().close();
    }
}
