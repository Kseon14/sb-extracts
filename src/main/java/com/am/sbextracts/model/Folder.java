package com.am.sbextracts.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class Folder {
    String sectionName;
    int sectionFileCount;
    int offset;
    boolean isSignatureTemplate;
    boolean permsAllowViewingSignatures;
    String html;
    boolean success;

}
