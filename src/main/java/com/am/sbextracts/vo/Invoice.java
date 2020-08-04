package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.context.ApplicationEvent;

public class Invoice extends ApplicationEvent {

    @Getter @Setter private String agreementNumber;
    @Getter @Setter private String agreementIssueDate;
    @Getter @Setter private String fullNameEng;
    @Getter @Setter private String fullNameUkr;
    @Getter @Setter private String addressEng;
    @Getter @Setter private String addressUrk;
    @Getter @Setter private String ipn;
    @Getter @Setter private String serviceEng;
    @Getter @Setter private String serviceUkr;
    @Getter @Setter private String price;
    @Getter @Setter private String accountNumberUsd;
    @Getter @Setter private String bankNameEng;
    @Getter @Setter private String bankAddress;
    @Getter @Setter private String swiftNumber;
    @Getter @Setter private String userEmail;
    @Getter @Setter private String authorSlackId;

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
