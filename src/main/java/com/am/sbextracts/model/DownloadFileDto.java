package com.am.sbextracts.model;

import lombok.Value;

@Value
public class DownloadFileDto {
    String sessionId;
    String date;
    String gFolderId;
}
