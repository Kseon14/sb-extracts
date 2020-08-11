package com.am.sbextracts.pool;

import com.hubspot.slack.client.SlackClient;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlackClientPool extends GenericObjectPool<SlackClient> {

    @Autowired
    public SlackClientPool(PooledObjectFactory<SlackClient> factory,
                          @Value("${pool.max-total}")Integer maxTotal,
                           @Value("${pool.max-idle}") Integer maxIdle) {
        super(factory);
        GenericObjectPoolConfig<SlackClient> poolConfigSlack = new GenericObjectPoolConfig<>();
        poolConfigSlack.setMaxIdle(maxIdle);
        poolConfigSlack.setJmxEnabled(true);
        poolConfigSlack.setJmxNamePrefix("slack-client-pool");
        poolConfigSlack.setMaxTotal(maxTotal);
        this.setConfig(poolConfigSlack);
    }

    public SlackClientPool(PooledObjectFactory<SlackClient> factory, GenericObjectPoolConfig<SlackClient> config) {
        super(factory, config);
    }


}
