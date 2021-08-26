package com.am.sbextracts.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class DocumentInfo {
    String inn;
    int fileId;
    int templateFileId;
}
