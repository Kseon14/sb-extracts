package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class Event {
    private Type type;
    private Type subtype;
    @JsonProperty("channel")
    private String channelId;
    private String user;
    @JsonProperty("files")
    private List<FileMetaInfo> fileMetaInfos;

    public enum Type {
        FILE_SHARE("file_share"),
        FILE_SHARED("file_shared"),
        MESSAGE_CHANGED("message_changed"),
        MESSAGE("message");

        @Getter
        @JsonValue
        private final String value;

        Type(String value) {
            this.value = value;
        }
    }
}