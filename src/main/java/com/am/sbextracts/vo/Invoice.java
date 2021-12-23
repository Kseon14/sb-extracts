package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.context.ApplicationEvent;

import java.util.Date;

@Getter
@Setter
public class Invoice extends ApplicationEvent {

    private String agreementNumber;
    private Date agreementIssueDate;
    private String fullNameEng;
    private String fullNameUkr;
    private String addressEng;
    private String addressUrk;
    private String ipn;
    private String serviceEng;
    private String serviceUkr;
    private String price;
    private String accountNumberUsd;
    private String bankNameEng;
    private String bankAddress;
    private String swiftNumber;
    private String userEmail;
    private String authorSlackId;

    public Invoice(Object source) {
        super(source);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("agreementNumber", agreementNumber)
                .append("agreementIssueDate", agreementIssueDate)
                .append("fullNameEng", fullNameEng)
                .append("fullNameUkr", fullNameUkr)
                .append("serviceEng", serviceEng)
                .append("serviceUkr", serviceUkr)
                .append("bankNameEng", bankNameEng)
                .append("bankAddress", bankAddress)
                .append("userEmail", userEmail)
                .append("authorSlackId", authorSlackId)
                .toString();
    }
}
