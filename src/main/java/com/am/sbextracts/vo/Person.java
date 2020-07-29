package com.am.sbextracts.vo;

import org.apache.commons.lang3.builder.ToStringBuilder;

import lombok.Getter;
import lombok.Setter;

public class Person {
   @Getter @Setter private String taxCode;
   @Getter @Setter private String fullName;
   @Getter @Setter private String amount;
   @Getter @Setter private String bankName;
   @Getter @Setter private String mfo;
   @Getter @Setter private String receiver;
   @Getter @Setter private String account;
   @Getter @Setter private String code;
   @Getter @Setter private String purposeOfPayment;
   @Getter @Setter private String userName;
   @Getter @Setter private String dueDate;
   @Getter @Setter private String taxType;
   @Getter @Setter private String author;

   @Override public String toString() {
      return new ToStringBuilder(this)
              .append("taxCode", taxCode)
              .append("fullName", fullName)
              .append("amount", amount)
              .append("bankName", bankName)
              .append("mfo", mfo)
              .append("receiver", receiver)
              .append("account", account)
              .append("code", code)
              .append("purposeOfPayment", purposeOfPayment)
              .append("userName", userName)
              .append("dueDate", dueDate)
              .append("taxType", taxType)
              .append("author", author)
              .toString();
   }
}
