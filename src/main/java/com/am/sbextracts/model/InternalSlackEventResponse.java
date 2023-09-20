package com.am.sbextracts.model;

import com.am.sbextracts.vo.SlackInteractiveEvent;
import com.am.sbextracts.vo.View;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import static com.am.sbextracts.service.SlackResponderService.DOCUMENT_SUFFIX;
import static com.am.sbextracts.service.SlackResponderService.SECTION_ID;
import static com.am.sbextracts.service.SlackResponderService.SESSION_ID;

@Data
@Builder
public class InternalSlackEventResponse {
    private final String sessionId;
    private final Integer folderId;
    private final String gFolderId;
    private final String date;
    private final String initiatorUserId;
    private final String[] typeOfDocuments;


    public static InternalSlackEventResponse convert(SlackInteractiveEvent slackInteractiveEvent) {
        Map<String, View.Item> values = slackInteractiveEvent.getView().getState().getValues();
        //2021.07.01
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        Optional<LocalDate> date = Optional.ofNullable(values.get("date").getField().getSelected_date());
        return InternalSlackEventResponse.builder()
                .sessionId(getFieldValue(values, SESSION_ID))
                .folderId(Optional.ofNullable(values.get(SECTION_ID)).map(f -> Integer.parseInt(f.getField()
                        .getValue())).orElse(null))
                .date(date.map(d -> d.format(formatter)).orElse(null))
                .gFolderId(getFieldValue(values, "gFolderId"))
                .initiatorUserId(slackInteractiveEvent.getUser().getId())
                .typeOfDocuments(Optional.ofNullable(values.get(DOCUMENT_SUFFIX))
                        .map(f -> f.getField().getSelected_option().getValue())
                        .map(f -> f.split(",")).orElse(null))
                .build();
    }

    @Nullable
    private static String getFieldValue(Map<String, View.Item> values, String fieldName) {
        return Optional.ofNullable(values.get(fieldName)).map(f -> f.getField().getValue()).orElse(null);
    }
}
