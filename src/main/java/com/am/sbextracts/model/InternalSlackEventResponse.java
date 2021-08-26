package com.am.sbextracts.model;

import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.am.sbextracts.vo.View;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Data
@Builder
public class InternalSlackEventResponse {
    private final String sessionId;
    private final Integer bambooFolderId;
    private final String gFolderId;
    private final String aktDate;
    private final String initiatorUserId;


    public static InternalSlackEventResponse convert(SlackInteractiveEvent slackInteractiveEvent){
        Map<String, View.Item> values = slackInteractiveEvent.getView().getState().getValues();
        //2021.07.01
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        Optional<LocalDate> date = Optional.ofNullable(values.get("date").getField().getSelected_date());
        return InternalSlackEventResponse.builder()
                .sessionId(Optional.ofNullable(values.get("sessionId")).map(f -> f.getField().getValue()).orElse(null))
                .bambooFolderId(Optional.ofNullable(values.get("sectionId")).map(f -> Integer.parseInt(f.getField().getValue())).orElse(null))
                .aktDate(date.map(d -> d.format(formatter)).orElse(null))
                .gFolderId(Optional.ofNullable(values.get("gFolderId")).map(f -> f.getField().getValue()).orElse(null))
                .initiatorUserId(slackInteractiveEvent.getUser().getId())
                .build();
    }
}
