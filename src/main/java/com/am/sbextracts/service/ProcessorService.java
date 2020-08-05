package com.am.sbextracts.service;

import com.am.sbextracts.vo.SlackEvent;

import java.io.IOException;
import java.io.InputStream;

public interface ProcessorService {

    void process(InputStream inputStream, SlackEvent.FileMetaInfo fileMetaInfo) throws IOException;
}
