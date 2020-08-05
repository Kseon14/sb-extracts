package com.am.sbextracts.service;

import com.am.sbextracts.vo.SlackEvent;

import java.util.List;

public interface FileDownloader {

    void downloadFile(List<SlackEvent.FileMetaInfo> fileMetaInfo);
}
