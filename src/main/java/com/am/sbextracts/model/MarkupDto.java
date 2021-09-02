package com.am.sbextracts.model;

import lombok.Value;

@Value
public class MarkupDto {
    int sectionId;
    String sessionId;
    String date;
    int totalToProcessing;
}
