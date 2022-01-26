package com.am.sbextracts.pool;

import com.slack.api.Slack;
import com.slack.api.methods.AsyncMethodsClient;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlackClientFactory extends BasePooledObjectFactory<AsyncMethodsClient> {

    @Value("${slack.token}")
    private String token;

    @Override
    public AsyncMethodsClient create() {
        Slack slack = Slack.getInstance();
        return slack.methodsAsync(token);
    }

    @Override
    public PooledObject<AsyncMethodsClient> wrap(AsyncMethodsClient slackClient) {
        return new DefaultPooledObject<>(slackClient);
    }

}
