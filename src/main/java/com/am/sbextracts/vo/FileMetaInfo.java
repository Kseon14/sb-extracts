package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class FileMetaInfo {
    private String id;
    private long timestamp;
    @JsonProperty("user")
    private String author;
    @JsonProperty("filetype")
    private String fileType;
    @ToString.Exclude
    @JsonProperty("url_private")
    private String urlPrivate;
    private String name;
}