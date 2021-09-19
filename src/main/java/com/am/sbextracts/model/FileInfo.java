package com.am.sbextracts.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class FileInfo {
    String fileName;
    String href;
    String id;

}
