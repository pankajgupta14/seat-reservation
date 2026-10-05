package com.paytm.seatreservation.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.paytm.seatreservation.repository.ShowRepository;

@Service
public class ReservationMetricsService {

	private final MeterRegistry meterRegistry;
	private final JdbcTemplate jdbcTemplate;
	private final ShowRepository showRepository;

	public ReservationMetricsService(MeterRegistry meterRegistry, JdbcTemplate jdbcTemplate, ShowRepository showRepository) {
		super();
		this.meterRegistry = meterRegistry;
		this.jdbcTemplate = jdbcTemplate;
		this.showRepository = showRepository;
	}

	public void recordConfirmed() {
		Counter.builder("reservations_confirmed_total").description("Total number of confirmed reservations")
				.register(meterRegistry).increment();
	}

	public void recordDeclined(String reason) {
		Counter.builder("reservations_declined_total").description("Total number of declined reservation attempts")
				.tag("reason", reason).register(meterRegistry).increment();
	}

	public void registerAvailableSeatsGauge(Long showId) {

		String metricName = "seats_available";
		String showIdTag = String.valueOf(showId);

		if (meterRegistry.find(metricName).tag("show_id", showIdTag).gauge() != null) {
			return;
		}

		Gauge.builder(metricName, () -> getAvailableSeats(showId)).description("Number of currently available seats")
				.tag("show_id", showIdTag).register(meterRegistry);
	}

	private double getAvailableSeats(Long showId) {

		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM show_seats
				WHERE show_id = ?
				  AND status = 'AVAILABLE'
				""", Long.class, showId);

		return count == null ? 0 : count;
	}
	
	@EventListener(ApplicationReadyEvent.class)
	public void registerExistingShowGauges() {
		showRepository.findAll().forEach(show -> registerAvailableSeatsGauge(show.getId()));
	}
}
