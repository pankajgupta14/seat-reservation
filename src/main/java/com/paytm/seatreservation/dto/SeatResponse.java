package com.paytm.seatreservation.dto;

import com.paytm.seatreservation.enums.SeatStatus;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeatResponse {

	private String seatNumber;
	private SeatStatus status;
}
