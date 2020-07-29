package com.am.sbextracts.vo;

import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.Setter;

public class SlackEvent {
    @Getter @Setter private Type type;
    @Getter @Setter private Event event;
    @Getter @Setter private String challenge;
    @Getter @Setter private String token;

    public enum Type {
        URL_VERIFICATION("url_verification"),
        EVENT_CALLBACK("event_callback");

        @Getter @JsonValue private final String value;

        Type(String value) {
            this.value = value;
        }
    }

    public static class Event {
        @Getter @Setter private Type type;
        @Getter @Setter private Type subtype;
        @Getter @Setter @JsonProperty("channel") private String channelId;
        @Getter @Setter private String user;
        @Getter @Setter @JsonProperty("files") private List<FileInfo> fileInfos;

        public enum Type {
            FILE_SHARE("file_share"),
            FILE_SHARED("file_shared"),
            MESSAGE("message");

            @Getter @JsonValue private final String value;

            Type(String value) {
                this.value = value;
            }
        }

        @Override public String toString() {
            return new ToStringBuilder(this)
                    .append("type", type)
                    .append("subtype", subtype)
                    .append("channelId", channelId)
                    .append("user", user)
                    .append("fileInfos", fileInfos)
                    .toString();
        }
    }

    public static class FileInfo {
        @Getter @Setter private String id;
        @Getter @Setter private long timestamp;
        @Getter @Setter private @JsonProperty("user") String author;
        @Getter @Setter @JsonProperty("filetype") private String fileType;
        @Getter @Setter @JsonProperty("url_private") private String urlPrivate;

        @Override public String toString() {
            return new ToStringBuilder(this)
                    .append("id", id)
                    .append("timestamp", timestamp)
                    .append("author", author)
                    .append("fileType", fileType)
                    .append("urlPrivate", urlPrivate)
                    .toString();
        }
    }

    @Override public String toString() {
        return new ToStringBuilder(this)
                .append("type", type)
                .append("event", event)
                .append("challenge", challenge)
                .append("token", token)
                .toString();
    }
}
