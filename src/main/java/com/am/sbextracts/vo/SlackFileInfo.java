package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlackFileInfo {

     @JsonProperty("file")
     private FileMetaInfo fileMetaInfo;

}
