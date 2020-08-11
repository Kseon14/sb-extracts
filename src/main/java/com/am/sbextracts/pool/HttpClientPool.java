package com.am.sbextracts.pool;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.asynchttpclient.AsyncHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpClientPool extends GenericObjectPool<AsyncHttpClient> {

    @Autowired
    public HttpClientPool(PooledObjectFactory<AsyncHttpClient> factory,
                          @Value("${pool.max-total}")Integer maxTotal,
                          @Value("${pool.max-idle}") Integer maxIdle) {
        super(factory);
        GenericObjectPoolConfig<AsyncHttpClient> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setJmxEnabled(true);
        poolConfig.setJmxNamePrefix("http-client-pool");
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setTestOnBorrow(true);
        this.setConfig(poolConfig);
    }

    public HttpClientPool(PooledObjectFactory<AsyncHttpClient> factory, GenericObjectPoolConfig<AsyncHttpClient> config) {
        super(factory, config);
    }

}
