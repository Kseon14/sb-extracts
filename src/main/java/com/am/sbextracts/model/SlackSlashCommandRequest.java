package com.am.sbextracts.model;

import lombok.Value;

@Value
public class SlackSlashCommandRequest {
    String user_id;
    String text;

}
