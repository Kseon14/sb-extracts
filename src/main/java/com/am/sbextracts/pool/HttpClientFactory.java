package com.am.sbextracts.pool;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class HttpClientFactory extends BasePooledObjectFactory<AsyncHttpClient> {
    @Override
    public AsyncHttpClient create() {
        return Dsl.asyncHttpClient();
    }

    @Override
    public PooledObject<AsyncHttpClient> wrap(AsyncHttpClient asyncHttpClient) {
        return new DefaultPooledObject<>(asyncHttpClient);
    }

    @Override
    public boolean validateObject(final PooledObject<AsyncHttpClient> pooledObject) {
        return !pooledObject.getObject().isClosed();
    }

    @Override
    public void destroyObject(final PooledObject<AsyncHttpClient> asyncHttpClientPooledObject) throws IOException {
        asyncHttpClientPooledObject.getObject().close();
    }
}
