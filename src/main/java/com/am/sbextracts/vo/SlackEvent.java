package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SlackEvent {
    private Type type;
    private Event event;
    private String challenge;
    @ToString.Exclude
    private String token;

    public enum Type {
        URL_VERIFICATION("url_verification"),
        EVENT_CALLBACK("event_callback");

        @Getter
        @JsonValue
        private final String value;

        Type(String value) {
            this.value = value;
        }
    }

}
