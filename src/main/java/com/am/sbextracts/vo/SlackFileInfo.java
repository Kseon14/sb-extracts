package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

public class SlackFileInfo {

    @Getter @Setter @JsonProperty("file") private SlackEvent.FileInfo fileInfo;

}
