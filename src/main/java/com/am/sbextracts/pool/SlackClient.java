package com.am.sbextracts.pool;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackClient {

    @Value("${slack.token}")
    private String token;

    private final Slack slack = Slack.getInstance();

    public MethodsClient getClient() {
        return slack.methods(token);
    }

    public void close() throws Exception {
        slack.close();
    }
}
