package com.paytm.seatreservation.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ShowStateResponse {

	private Long id;
    private String name;
    private Long pricePaise;

    private int totalSeats;
    private int availableSeats;
    private int heldSeats;
    private int confirmedSeats;

    private List<SeatResponse> seats;
    
   
}
