package com.am.sbextracts.pool;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlackClientFactory extends BasePooledObjectFactory<MethodsClient> {

    @Value("${slack.token}")
    private String token;

    @Override
    public MethodsClient create() {
        Slack slack = Slack.getInstance();
        return slack.methods(token);
    }

    @Override
    public PooledObject<MethodsClient> wrap(MethodsClient slackClient) {
        return new DefaultPooledObject<>(slackClient);
    }

}
