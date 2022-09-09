package com.am.sbextracts.service;

import com.am.sbextracts.vo.FileMetaInfo;

import java.io.IOException;
import java.io.InputStream;

public interface ProcessorService {

    void process(InputStream inputStream, FileMetaInfo fileMetaInfo) throws IOException;
}
