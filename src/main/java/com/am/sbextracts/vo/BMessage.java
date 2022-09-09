package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.util.Date;
import java.util.Set;

@Getter
@Setter
@ToString
public class BMessage extends ApplicationEvent {

    private String fullName;
    private Date dueDate;
    private String text;
    private String userEmail;
    private String authorSlackId;
    private boolean withEmail;
    private Set<String> additionalUserEmail;

    public BMessage(Object source) {
        super(source);
    }

}
