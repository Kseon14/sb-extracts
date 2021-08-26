package com.am.sbextracts.service.integration.utils;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.OutputStream;
import java.io.PrintStream;

public class Interceptor extends PrintStream {

    @Getter
    private String url;

    public Interceptor(OutputStream out) {
        super(out, true);
    }
    @Override
    public void print(String s) {
        super.print(s);
        if(StringUtils.startsWithIgnoreCase("https://accounts.google.com", s)) {
            this.url = s;
        }
    }
}
