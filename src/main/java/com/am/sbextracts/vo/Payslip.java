package com.am.sbextracts.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

@Setter
@Getter
@ToString
public class Payslip extends ApplicationEvent {

    private String fullName;
    @ToString.Exclude
    private String contractRate;
    @ToString.Exclude
    private String otherIncome;
    private String socialTax;
    private String insurance;
    @ToString.Exclude
    private String bonus;
    private String currencyRate;
    @ToString.Exclude
    private String totalNet;
    @ToString.Exclude
    private String currentPaymentTax;
    @ToString.Exclude
    private String totalGross;
    private String userEmail;
    private String authorSlackId;

    public Payslip(Object source) {
        super(source);
    }

}
