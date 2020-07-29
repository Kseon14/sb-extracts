package com.am.sbextracts.service;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Service;

import com.am.sbextracts.vo.SlackResponse;

public interface Processor {

    SlackResponse process(InputStream inputStream) throws IOException;
}
