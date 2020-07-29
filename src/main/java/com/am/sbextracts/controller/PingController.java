package com.am.sbextracts.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ping")
public class PingController {

    @PostMapping("challenge")
    public String urlVerification(@RequestBody Map<String, String> param) {
        return param.get("challenge");
    }
}
