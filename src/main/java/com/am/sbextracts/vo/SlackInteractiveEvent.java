package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.Getter;

import org.apache.commons.lang3.builder.ToStringBuilder;

@Data
public class SlackInteractiveEvent {
    private Type type;
    private String triggerId;
    private String userId;
    private Action action;
    private String token;
    private View view;
    private User user;

    @Data
    public static class User {
        String id;
    }

    public enum Type {
        EVENT_CALLBACK("event_callback"),
        BLOCK_ACTIONS("block_actions"),
        VIEW_SUBMISSION("view_submission");

        @Getter @JsonValue private final String value;

        Type(String value) {
            this.value = value;
        }
    }

    @Override public String toString() {
        return new ToStringBuilder(this)
                .append("type", type)
                .append("token", token)
                .toString();
    }

    @Data
    public static class Action {
       private Type type;
    }
}
