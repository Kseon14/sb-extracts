package com.am.sbextracts.pool;

import com.slack.api.methods.MethodsClient;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlackClientPool extends GenericObjectPool<MethodsClient> {

    @Autowired
    public SlackClientPool(PooledObjectFactory<MethodsClient> factory,
                           @Value("${pool.max-total}") Integer maxTotal,
                           @Value("${pool.max-idle}") Integer maxIdle) {
        super(factory);
        GenericObjectPoolConfig<MethodsClient> poolConfigSlack = new GenericObjectPoolConfig<>();
        poolConfigSlack.setMaxIdle(maxIdle);
        poolConfigSlack.setJmxEnabled(true);
        poolConfigSlack.setJmxNamePrefix("slack-client-pool");
        poolConfigSlack.setMaxTotal(maxTotal);
        this.setConfig(poolConfigSlack);
    }

    public SlackClientPool(PooledObjectFactory<MethodsClient> factory, GenericObjectPoolConfig<MethodsClient> config) {
        super(factory, config);
    }


}
