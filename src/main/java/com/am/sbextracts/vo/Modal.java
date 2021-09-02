package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class Modal {
    View view;
    @JsonProperty("trigger_id")
    String triggerID;
}
