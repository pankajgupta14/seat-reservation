package com.paytm.seatreservation.exception;

public class SeatAlreadyTakenException extends RuntimeException {

    public SeatAlreadyTakenException(String message) {
        super(message);
    }
}