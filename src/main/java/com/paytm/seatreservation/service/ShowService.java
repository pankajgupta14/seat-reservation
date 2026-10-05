package com.paytm.seatreservation.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.CreateShowResponse;
import com.paytm.seatreservation.dto.SeatResponse;
import com.paytm.seatreservation.dto.ShowStateResponse;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.entity.ShowSeat;
import com.paytm.seatreservation.enums.SeatStatus;
import com.paytm.seatreservation.exception.ShowNotFoundException;
import com.paytm.seatreservation.repository.ShowRepository;
import com.paytm.seatreservation.repository.ShowSeatRepository;


@Service
public class ShowService {

	private final ShowRepository showRepository;
	private final ShowSeatRepository showSeatRepository;
	private final JdbcTemplate jdbcTemplate;
	private final ReservationMetricsService metricsService;

	public ShowService(ShowRepository showRepository, ShowSeatRepository showSeatRepository,
			JdbcTemplate jdbcTemplate, ReservationMetricsService metricsService) {
		super();
		this.showRepository = showRepository;
		this.showSeatRepository = showSeatRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.metricsService = metricsService;
	}

	@Transactional
	public CreateShowResponse createShow(CreateShowRequest request) {

		Show show = new Show();
		show.setName(request.getName());
		show.setPricePaise(request.getPricePaise());
		show.setPerUserLimit(request.getPerUserLimit() != null ? request.getPerUserLimit() : 4);

		show = showRepository.save(show);

		// Batch insert all seats
		String sql = """
				INSERT INTO show_seats
				    (show_id, seat_number, status)
				VALUES
				    (?, ?, ?)
				""";

		Long showId = show.getId();

		jdbcTemplate.batchUpdate(sql, request.getSeats(), request.getSeats().size(), (ps, seatNumber) -> {
			ps.setLong(1, showId);
			ps.setString(2, seatNumber);
			ps.setString(3, SeatStatus.AVAILABLE.name());
		});
		
		metricsService.registerAvailableSeatsGauge(show.getId());
		
		return new CreateShowResponse(show.getId(), show.getName(), request.getSeats(), show.getPricePaise(),
				show.getPerUserLimit());
	}

	
	
	@Transactional(readOnly = true)
	public ShowStateResponse getShowState(Long showId) {

		Show show = showRepository.findById(showId)
				.orElseThrow(() -> new ShowNotFoundException("Show not found: " + showId));
		List<ShowSeat> seats = showSeatRepository.findByShowId(showId);

		int available = 0;
		int held = 0;
		int confirmed = 0;

		List<SeatResponse> seatResponses = new ArrayList<>();

		for (ShowSeat seat : seats) {
			switch (seat.getStatus()) {
			case AVAILABLE -> available++;
			case HELD -> held++;
			case CONFIRMED -> confirmed++;
			}
			seatResponses.add(new SeatResponse(seat.getSeatNumber(), seat.getStatus()));
		}
		int total = seats.size();

		return new ShowStateResponse(show.getId(), show.getName(), show.getPricePaise(), total, available, held,
				confirmed, seatResponses);
	}

}
