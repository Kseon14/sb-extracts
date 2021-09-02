package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;

public class SlackInteractiveEvent {
    @Getter @Setter private Type type;
    @Getter @Setter  private String trigger_id;
    @Getter @Setter  private String user_id;

    @Getter @Setter private Action action;

    @Getter @Setter private String token;
    @Getter @Setter private View view;

    @Getter @Setter private User user;


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
