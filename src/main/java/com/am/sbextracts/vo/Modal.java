package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Modal {
    View view;
    @JsonProperty("trigger_id")
    String triggerID;
}
