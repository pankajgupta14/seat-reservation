package com.paytm.seatreservation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.CreateShowResponse;
import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveSeatRequest;
import com.paytm.seatreservation.dto.ShowStateResponse;
import com.paytm.seatreservation.service.ReservationService;
import com.paytm.seatreservation.service.ShowService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/shows")
public class ShowController {

	private final ShowService showService;
	private final ReservationService reservationService;

	public ShowController(ShowService showService, ReservationService reservationService) {
		this.showService = showService;
		this.reservationService = reservationService;
	}

	@PostMapping
	public ResponseEntity<CreateShowResponse> createShow(@Valid @RequestBody CreateShowRequest request) {
		CreateShowResponse response = showService.createShow(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{showId}")
	public ResponseEntity<ShowStateResponse> getShow(@PathVariable Long showId) {
		return ResponseEntity.ok(showService.getShowState(showId));
	}

	@PostMapping("/{showId}/reserve")
	public ResponseEntity<ReservationResponse> reserve(@PathVariable Long showId,@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                      @Valid @RequestBody ReserveSeatRequest request,@AuthenticationPrincipal String userId) {

		ReservationResponse response = reservationService.reserve(showId, userId, request.getSeats(), idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
