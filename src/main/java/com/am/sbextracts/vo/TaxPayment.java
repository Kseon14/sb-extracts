package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.context.ApplicationEvent;

import java.util.Date;

public class TaxPayment extends ApplicationEvent {

    @Getter @Setter private String taxCode;
    @Getter @Setter private String fullName;
    @Getter @Setter private String amount;
    @Getter @Setter private String bankName;
    @Getter @Setter private String mfo;
    @Getter @Setter private String receiver;
    @Getter @Setter private String account;
    @Getter @Setter private String code;
    @Getter @Setter private String purposeOfPayment;
    @Getter @Setter private String userEmail;
    @Getter @Setter private Date dueDate;
    @Getter @Setter private String taxType;
    @Getter @Setter private String authorSlackId;

    public TaxPayment(Object source) {
        super(source);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("fullName", fullName)
                .append("bankName", bankName)
                .append("mfo", mfo)
                .append("receiver", receiver)
                .append("account", account)
                .append("purposeOfPayment", purposeOfPayment)
                .append("userEmail", userEmail)
                .append("dueDate", dueDate)
                .append("taxType", taxType)
                .append("authorSlackId", authorSlackId)
                .toString();
    }
}
