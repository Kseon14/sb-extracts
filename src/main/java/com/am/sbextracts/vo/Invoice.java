package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.util.Date;

@Getter
@Setter
@ToString
public class Invoice extends ApplicationEvent {

    private String agreementNumber;
    private Date agreementIssueDate;
    private String fullNameEng;
    private String fullNameUkr;
    private String addressEng;
    private String addressUrk;
    @ToString.Exclude
    private String ipn;
    private String serviceEng;
    private String serviceUkr;
    @ToString.Exclude
    private String price;
    private String accountNumberUsd;
    private String bankNameEng;
    private String bankAddress;
    @ToString.Exclude
    private String swiftNumber;
    private String userEmail;
    private String authorSlackId;
    private Date optionalDate;

    public Invoice(Object source) {
        super(source);
    }

}
