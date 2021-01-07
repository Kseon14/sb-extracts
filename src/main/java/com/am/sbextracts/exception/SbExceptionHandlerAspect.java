package com.am.sbextracts.exception;

import com.am.sbextracts.service.ResponderService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class SbExceptionHandlerAspect {

    private final ResponderService responderService;

    @AfterThrowing(value = "@annotation(SbExceptionHandler)", throwing = "e")
    public void reportError(SbExtractsException e) {
        responderService.sendErrorMessageToInitiator(e.getSendTo(), "ERROR",
                composeErrorMessage(e));
    }

    private String composeErrorMessage(SbExtractsException e) {
        if (StringUtils.isNotBlank(e.getAffectedUserEmail())) {
            return String.format("Error for <%s> | %s: %s", e.getAffectedUserEmail(),
                    e.getMessage(), e.getCause().getMessage());
        }
        return String.format("%s: %s", e.getMessage(), e.getCause().getMessage());
    }
}
