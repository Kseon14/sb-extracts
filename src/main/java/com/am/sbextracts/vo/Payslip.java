package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.context.ApplicationEvent;

@Setter
@Getter
public class Payslip extends ApplicationEvent {

    private String fullName;
    private String contractRate;
    private String otherIncome;
    private String socialTax;
    private String insurance;
    private String bonus;
    private String currencyRate;
    private String totalNet;
    private String currentPaymentTax;
    private String totalGross;
    private String userEmail;
    private String authorSlackId;

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
                .append("bonus", bonus)
                .append("currencyRate", currencyRate)
                .append("currentPaymentTax", currentPaymentTax)
                .append("userEmail", userEmail)
                .append("authorSlackId", authorSlackId)
                .toString();
    }
}
