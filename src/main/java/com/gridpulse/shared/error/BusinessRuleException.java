package com.gridpulse.shared.error;

/** 422 Unprocessable: the request is well-formed but breaks a business rule. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
