package com.gridpulse.session;

/** The charger or the vehicle is already booked for an overlapping window. */
public class ReservationConflictException extends RuntimeException {
    public ReservationConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
