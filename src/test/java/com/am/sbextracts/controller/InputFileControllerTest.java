package com.am.sbextracts.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.am.sbextracts.service.Processor;

@AutoConfigureMockMvc
@SpringBootTest
class InputFileControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    public void testParsing() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile("file", "2020.xlsx",
                "text/plain", this.getClass().getResourceAsStream("2020.xlsx"));
        this.mvc.perform(multipart("/api/files").file(multipartFile))
                .andExpect(status().isOk());

    }

}