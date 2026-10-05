package com.paytm.seatreservation.exception;

public class BookingLimitExceededException extends RuntimeException {

    public BookingLimitExceededException(String message) {
        super(message);
    }

}
