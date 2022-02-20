package com.am.sbextracts.exception;

import lombok.Getter;

public class SbExtractsException extends RuntimeException {

    @Getter
    private final String sendTo;
    @Getter
    private String affectedUserEmail;

    public SbExtractsException(String message, String affectedUserEmail, String sendTo) {
        super(message);
        this.sendTo = sendTo;
        this.affectedUserEmail = affectedUserEmail;
    }

    public SbExtractsException(String message, String sendTo) {
        super(message);
        this.sendTo = sendTo;
    }

    public SbExtractsException(String message, Throwable cause, String affectedUserEmail, String sendTo) {
        super(message, cause);
        this.sendTo = sendTo;
        this.affectedUserEmail = affectedUserEmail;
    }

    public SbExtractsException(String message, Throwable cause, String sendTo) {
        super(message, cause);
        this.sendTo = sendTo;
    }
}
