package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@ToString
public class SlackResponse {

    public SlackResponse(String text) {
        this.responseType = "im";
        this.text = text;
    }

    @Getter
    @Setter
    private String text;

    @JsonProperty("response_type")
    private String responseType;

}
