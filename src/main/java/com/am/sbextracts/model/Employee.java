package com.am.sbextracts.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class Employee {
    String id;
    @JsonProperty("customID#")
    String inn;
}
