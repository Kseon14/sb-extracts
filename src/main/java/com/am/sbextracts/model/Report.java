package com.am.sbextracts.model;

import lombok.Value;

import java.util.List;

@Value
public class Report {
    String title;
    List<Employee> employees;
}
