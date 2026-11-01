package com.gridpulse.shared.error;

/** 404 Not Found: a resource does not exist or belongs to another tenant. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }
}
