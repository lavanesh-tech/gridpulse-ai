package com.gridpulse.session;

import com.gridpulse.shared.error.ConflictException;

/** The charger or the vehicle is already booked for an overlapping window. */
public class ReservationConflictException extends ConflictException {
    public ReservationConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
