package com.am.sbextracts.service;

import java.util.List;

import com.am.sbextracts.vo.SlackEvent;

public interface FileDownloader {

    void downloadFile(List<SlackEvent.FileInfo> fileInfo);
}
