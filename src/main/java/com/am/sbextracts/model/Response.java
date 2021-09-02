package com.am.sbextracts.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Response {
    String html;
    boolean success;
    String esignatureTemplateId;
    String message;
    String error;
    String workflowId;
}
