package com.am.sbextracts.service;

import java.io.IOException;
import java.io.InputStream;

import com.am.sbextracts.vo.SlackResponse;

public interface Processor {

    SlackResponse process(InputStream inputStream, String authorId) throws IOException;
}
