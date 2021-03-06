package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.context.ApplicationEvent;

import java.util.Date;

public class BMessage extends ApplicationEvent {

    @Getter @Setter private String fullName;
    @Getter @Setter private Date dueDate;
    @Getter @Setter private String text;
    @Getter @Setter private String userEmail;
    @Getter @Setter private String authorSlackId;


    public BMessage(Object source) {
        super(source);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("name", fullName)
                .append("date", dueDate)
                .append("text", text)
                .append("userEmail", userEmail)
                .append("authorSlackId", authorSlackId)
                .toString();
    }
}
