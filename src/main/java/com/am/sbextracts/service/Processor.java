package com.am.sbextracts.service;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Service;

public interface Processor {

    void process(InputStream inputStream) throws IOException;
}
