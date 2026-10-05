package com.paytm.seatreservation.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CreateShowResponse {

	private Long id;
    private String name;
    private List<String> seats;
    private Long pricePaise;
    private Integer perUserLimit;
}
