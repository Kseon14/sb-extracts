package com.am.sbextracts.service;

import com.am.sbextracts.vo.FileMetaInfo;

import java.util.List;

public interface FileDownloader {

    void downloadFile(List<FileMetaInfo> fileMetaInfo);
}
