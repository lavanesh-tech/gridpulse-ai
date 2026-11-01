package com.gridpulse.shared.error;

/** 409 Conflict: the request clashes with current state (double booking, duplicate). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
