package com.am.sbextracts.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.lang3.EnumUtils;

import java.time.LocalDate;
import java.util.Map;

@Data
public class View {
    State state;
    @JsonProperty("callback_id")
    ModalActionType type;

    @Data
    public static class Item {
        Field field;
    }

    @Data
    public static class Field {
        String value;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate selected_date;
    }

    @Data
    public static class State {
        Map<String, Item> values;
    }

    public enum ModalActionType {
        MARKUP,
        SIGNED,
        DEBTORS,
        INVOICE_DOWNLOAD;

        @JsonCreator
        public static ModalActionType forValue(String value) {
            return EnumUtils.getEnum(ModalActionType.class, value);
        }

    }

}
