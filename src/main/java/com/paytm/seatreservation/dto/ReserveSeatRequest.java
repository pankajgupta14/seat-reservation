package com.paytm.seatreservation.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public class ReserveSeatRequest {

	@NotEmpty
	private List<String> seats;
	
    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }
}
