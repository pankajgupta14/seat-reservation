package com.paytm.seatreservation.exception;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(SeatAlreadyTakenException.class)
	public ResponseEntity<?> seatTaken(SeatAlreadyTakenException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(error("SEAT_TAKEN", ex.getMessage()));
	}

	@ExceptionHandler(BookingLimitExceededException.class)
	public ResponseEntity<?> bookingLimit(BookingLimitExceededException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(error("PER_USER_LIMIT", ex.getMessage()));
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<?> idempotencyConflict(IdempotencyConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(error("IDEMPOTENCY_CONFLICT", ex.getMessage()));
	}

	@ExceptionHandler(ShowNotFoundException.class)
	public ResponseEntity<?> showNotFound(ShowNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("SHOW_NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("BAD_REQUEST", ex.getMessage()));
	}

	private Map<String, Object> error(String code, String message) {
		return Map.of("timestamp", LocalDateTime.now(), "code", code, "message", message);
	}
	
	@ExceptionHandler(ReservationNotFoundException.class)
	public ResponseEntity<?> reservationNotFound(ReservationNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("RESERVATION_NOT_FOUND", ex.getMessage()));
	}
	
	@ExceptionHandler(ReservationOwnershipException.class)
	public ResponseEntity<?> ownershipError(ReservationOwnershipException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("RESERVATION_NOT_OWNER", ex.getMessage()));
	}
	
	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> internalServerError(Exception ex) {

	    ex.printStackTrace();

	    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	            .body(error(
	                    "INTERNAL_SERVER_ERROR",
	                    ex.getClass().getSimpleName() + ": " + ex.getMessage()
	            ));
	}
}
