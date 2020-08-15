package com.am.sbextracts.exception;

import com.am.sbextracts.service.ResponderService;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SbExceptionHandlerAspect {

    private final ResponderService responderService;

    public SbExceptionHandlerAspect(ResponderService responderService) {
        this.responderService = responderService;
    }

    @AfterThrowing(value = "@annotation(SbExceptionHandler)", throwing = "e")
    public void reportError(SbExtractsException e) {
        responderService.sendErrorMessageToInitiator(e.getSendTo(), "ERROR",
                String.format("for <%s> | %s: %s", e.getAffectedUserEmail(), e.getMessage(), e.getCause().getMessage()));
    }
}
