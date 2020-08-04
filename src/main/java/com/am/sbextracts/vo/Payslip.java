package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.context.ApplicationEvent;

public class Payslip extends ApplicationEvent {

    @Getter @Setter private String fullName;
    @Getter @Setter private String contractRate;
    @Getter @Setter private String otherIncome;
    @Getter @Setter private String socialTax;
    @Getter @Setter private String insurance;
    @Getter @Setter private String rent;
    @Getter @Setter private String currencyRate;
    @Getter @Setter private String totalNet;
    @Getter @Setter private String currentPaymentTax;
    @Getter @Setter private String totalGross;
    @Getter @Setter private String userEmail;
    @Getter @Setter private String authorSlackId;

    public Payslip(Object source) {
        super(source);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("fullName", fullName)
                .append("otherIncome", otherIncome)
                .append("socialTax", socialTax)
                .append("insurance", insurance)
                .append("rent", rent)
                .append("currencyRate", currencyRate)
                .append("currentPaymentTax", currentPaymentTax)
                .append("userEmail", userEmail)
                .append("authorSlackId", authorSlackId)
                .toString();
    }
}
