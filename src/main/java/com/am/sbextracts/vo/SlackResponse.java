package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlackResponse {

    public SlackResponse(String text) {
        this.responseType = "im";
        this.text = text;
    }

    public SlackResponse() {
    }

    //@JsonProperty("text")
    @Getter
    @Setter
    private String text;

    @JsonProperty("response_type")
    private String responseType;

}
