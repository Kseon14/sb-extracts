package com.am.sbextracts.pool;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.asynchttpclient.AsyncHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpClientPool extends GenericObjectPool<AsyncHttpClient> {

    @Autowired
    public HttpClientPool(PooledObjectFactory<AsyncHttpClient> factory) {
        super(factory);
        GenericObjectPoolConfig<AsyncHttpClient> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(3);
        poolConfig.setJmxEnabled(true);
        poolConfig.setJmxNamePrefix("http-client-pool");
        poolConfig.setMaxTotal(5);
        poolConfig.setMinIdle(0);
        this.setConfig(poolConfig);
    }

    public HttpClientPool(PooledObjectFactory<AsyncHttpClient> factory, GenericObjectPoolConfig<AsyncHttpClient> config) {
        super(factory, config);
    }

}
