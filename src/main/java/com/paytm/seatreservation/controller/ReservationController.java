package com.paytm.seatreservation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.service.ReservationService;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping("/{reservationId}/cancel")
	public ResponseEntity<ReservationResponse> cancel(@PathVariable String reservationId,@AuthenticationPrincipal String userId) {
		return ResponseEntity.ok(reservationService.cancel(reservationId, userId));
	}
}