package com.am.sbextracts.service;

import java.io.IOException;
import java.io.InputStream;

import com.am.sbextracts.vo.SlackEvent;

public interface ProcessorService {

    void process(InputStream inputStream, SlackEvent.FileMetaInfo fileMetaInfo) throws IOException;
}
