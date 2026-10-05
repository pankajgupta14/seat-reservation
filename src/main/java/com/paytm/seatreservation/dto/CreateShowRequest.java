package com.paytm.seatreservation.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Data
public class CreateShowRequest {

	@NotBlank
	private String name;

	@NotEmpty
	private List<String> seats;

	@NotNull
	@Positive
	private Long pricePaise;

	private Integer perUserLimit = 4;
}
