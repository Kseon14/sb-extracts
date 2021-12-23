package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.context.ApplicationEvent;

import java.util.Date;

@Getter
@Setter
public class TaxPayment extends ApplicationEvent {

    private String taxCode;
    private String fullName;
    private String amount;
    private String receiver;
    private String account;
    private String code;
    private String purposeOfPayment;
    private String userEmail;
    private Date dueDate;
    private String taxType;
    private String authorSlackId;

    public TaxPayment(Object source) {
        super(source);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("fullName", fullName)
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
