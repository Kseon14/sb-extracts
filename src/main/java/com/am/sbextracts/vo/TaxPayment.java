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
public class TaxPayment extends ApplicationEvent {

    @ToString.Exclude
    private String taxCode;
    private String fullName;
    @ToString.Exclude
    private String amount;
    private String receiver;
    private String account;
    private String code;
    private String purposeOfPayment;
    private String userEmail;
    private Set<String> additionalUserEmail;
    private Date dueDate;
    private String taxType;
    private String authorSlackId;
    private boolean withEmail;

    public TaxPayment(Object source) {
        super(source);
    }

}
