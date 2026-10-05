package com.paytm.seatreservation.dto;

import java.util.List;

public class ReservationResponse {

	private String reservationId;
	private Long showId;
	private String userId;
	private List<String> seats;
	private Long amountPaise;
	private String status;

	public ReservationResponse(String reservationId, Long showId, String userId, java.util.List<String> seats,
			Long amountPaise, String status) {

		this.reservationId = reservationId;
		this.showId = showId;
		this.userId = userId;
		this.seats = seats;
		this.amountPaise = amountPaise;
		this.status = status;
	}

	public String getReservationId() {
		return reservationId;
	}

	public Long getShowId() {
		return showId;
	}

	public String getUserId() {
		return userId;
	}

	public java.util.List<String> getSeats() {
		return seats;
	}

	public Long getAmountPaise() {
		return amountPaise;
	}

	public String getStatus() {
		return status;
	}
}
