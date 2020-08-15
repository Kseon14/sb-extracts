package com.am.sbextracts.controller;

import com.am.sbextracts.exception.SbExtractsException;
import com.am.sbextracts.service.ResponderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionController {
    private final static Logger LOGGER = LoggerFactory.getLogger(ExceptionController.class);

    private final ResponderService responderService;

    public ExceptionController(ResponderService responderService) {
        this.responderService = responderService;
    }

    @ExceptionHandler(value = SbExtractsException.class)
    public void exception(SbExtractsException e) {

    }

}
