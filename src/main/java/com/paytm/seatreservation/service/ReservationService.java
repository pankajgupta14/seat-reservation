package com.paytm.seatreservation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.entity.ShowSeat;
import com.paytm.seatreservation.enums.ReservationStatus;
import com.paytm.seatreservation.exception.BookingLimitExceededException;
import com.paytm.seatreservation.exception.IdempotencyConflictException;
import com.paytm.seatreservation.exception.ReservationNotFoundException;
import com.paytm.seatreservation.exception.ReservationOwnershipException;
import com.paytm.seatreservation.exception.SeatAlreadyTakenException;
import com.paytm.seatreservation.exception.ShowNotFoundException;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import com.paytm.seatreservation.repository.ShowSeatRepository;

@Service
public class ReservationService {
	
	private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

	private final ShowRepository showRepository;
	private final ReservationRepository reservationRepository;
	private final JdbcTemplate jdbcTemplate;
	private final ReservationMetricsService metricsService;
	private final ShowSeatRepository showSeatRepository ;


	public ReservationService(ShowRepository showRepository, ReservationRepository reservationRepository,
			JdbcTemplate jdbcTemplate, ReservationMetricsService metricsService, ShowSeatRepository showSeatRepository) {

		this.showRepository = showRepository;
		this.reservationRepository = reservationRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.metricsService = metricsService;
		this.showSeatRepository = showSeatRepository;
	}

	@Transactional
	public ReservationResponse reserve(Long showId, String userId, List<String> requestedSeats, String idempotencyKey) {
		
			log.info("reservation_started showId={} userId={} seats={} idempotencyKey={}", showId, userId, requestedSeats,idempotencyKey);
			// 1. Validate and normalize seats
			List<String> seats = normalizeSeats(requestedSeats);

			// 2. Load show
			Show show = showRepository.findById(showId)
					.orElseThrow(() -> new ShowNotFoundException("Show not found: " + showId));

			// 3. Lock this user + show.
			//
			// This serializes booking attempts from the same user
			// for the same show.
			acquireBookingLock(showId, userId);

			// 4. Handle idempotency
			String requestHash = createRequestHash(showId, seats);

			jdbcTemplate.update("""
			        INSERT INTO idempotency_keys
			            (user_id, show_id, idempotency_key, request_hash, created_at)
			        VALUES (?, ?, ?, ?, NOW())
			        ON DUPLICATE KEY UPDATE
			            id = LAST_INSERT_ID(id)
			        """,
			        userId,
			        showId,
			        idempotencyKey,
			        requestHash
			);

			IdempotencyRecord idempotencyRecord =getIdempotencyRecord(userId, showId, idempotencyKey);
			
			if (!idempotencyRecord.requestHash().equals(requestHash)) {
				metricsService.recordDeclined("IDEMPOTENCY_CONFLICT");
				throw new IdempotencyConflictException("Same Idempotency-Key was used with different seats");
			}

			// Retry of an already completed request
			if (idempotencyRecord.reservationId() != null) {

			    Reservation existing = reservationRepository
			            .findById(idempotencyRecord.reservationId())
			            .orElse(null);

			    if (existing != null) {
			        List<String> existingSeats =
			                findSeatsByReservationId(existing.getId());

			        return toResponse(existing, existingSeats);
			    }

			    // Defensive recovery if the idempotency record
			    // points to a reservation that does not exist.
			    jdbcTemplate.update("""
			            UPDATE idempotency_keys
			            SET reservation_id = NULL
			            WHERE user_id = ?
			              AND show_id = ?
			              AND idempotency_key = ?
			            """,
			            userId,
			            showId,
			            idempotencyKey
			    );
			}
			
			// 5. Per-user seat limit
			long alreadyBooked = countConfirmedSeats(showId, userId);

			if (alreadyBooked + seats.size() > show.getPerUserLimit()) {
				metricsService.recordDeclined("PER_USER_LIMIT");
				throw new BookingLimitExceededException(
						"User can reserve maximum " + show.getPerUserLimit() + " seats for this show");
			}

			// 6. Generate reservation ID
			String reservationId = UUID.randomUUID().toString();

			// 7. Lock and verify every requested seat
			//
			// Seats are sorted before locking.
			// This deterministic order reduces deadlock risk.
			List<Long> seatIds = new ArrayList<>();

			for (String seat : seats) {

				Long seatId = jdbcTemplate.query("""
						SELECT id
						FROM show_seats
						WHERE show_id = ?
						  AND seat_number = ?
						FOR UPDATE
						""", rs -> rs.next() ? rs.getLong("id") : null, showId, seat);

				if (seatId == null) {
					throw new IllegalArgumentException("Seat does not exist: " + seat);
				}

				String status = jdbcTemplate.query("""
						SELECT status
						FROM show_seats
						WHERE id = ?
						""", rs -> rs.next() ? rs.getString("status") : null, seatId);

				if (!"AVAILABLE".equals(status)) {
					 metricsService.recordDeclined("SEAT_TAKEN");
					 log.info(
						        "reservation_declined reason=SEAT_TAKEN showId={} userId={} seat={}",
						        showId,
						        userId,
						        seats.toString()
						);
					throw new SeatAlreadyTakenException("Seat is not available: " + seat);
				}

				seatIds.add(seatId);
			}

			// 8. Create reservation
			long amountPaise = Math.multiplyExact(show.getPricePaise(), seats.size());

			Reservation reservation = new Reservation();

			reservation.setId(reservationId);
			reservation.setShow(show);
			reservation.setUserId(userId);
			reservation.setAmountPaise(amountPaise);
			reservation.setStatus(com.paytm.seatreservation.enums.ReservationStatus.CONFIRMED);

			reservationRepository.save(reservation);

			// 9. Mark seats CONFIRMED
			for (Long seatId : seatIds) {

				int updated = jdbcTemplate.update("""
						UPDATE show_seats
						SET status = 'CONFIRMED',
						    reservation_id = ?
						WHERE id = ?
						  AND status = 'AVAILABLE'
						""", reservationId, seatId);

				if (updated != 1) {
					 metricsService.recordDeclined("SEAT_TAKEN");
					throw new SeatAlreadyTakenException("Seat became unavailable");
				}
			}

			// 10. Complete idempotency record
			jdbcTemplate.update("""
					UPDATE idempotency_keys
					SET reservation_id = ?
					WHERE user_id = ?
					  AND show_id = ?
					  AND idempotency_key = ?
					""", reservationId, userId, showId, idempotencyKey);

			metricsService.recordConfirmed();
			return new ReservationResponse(reservationId, showId, userId, seats, amountPaise, "CONFIRMED");
	}

	private void acquireBookingLock(Long showId, String userId) {

		jdbcTemplate.update("""
				INSERT INTO booking_locks(show_id, user_id)
				VALUES (?, ?)
				ON DUPLICATE KEY UPDATE id = id
				""", showId, userId);

		jdbcTemplate.query("""
				SELECT id
				FROM booking_locks
				WHERE show_id = ?
				  AND user_id = ?
				FOR UPDATE
				""", rs -> {
			if (rs.next()) {
				rs.getLong("id");
			}
		}, showId, userId);
	}

	private IdempotencyRecord getIdempotencyRecord(String userId, Long showId, String idempotencyKey) {

		return jdbcTemplate.queryForObject("""
				SELECT request_hash, reservation_id
				FROM idempotency_keys
				WHERE user_id = ?
				  AND show_id = ?
				  AND idempotency_key = ?
				FOR UPDATE
				""",
				(rs, rowNum) -> new IdempotencyRecord(rs.getString("request_hash"), rs.getString("reservation_id")),
				userId, showId, idempotencyKey);
	}

	private long countConfirmedSeats(Long showId, String userId) {

		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM show_seats ss
				JOIN reservations r
				  ON r.id = ss.reservation_id
				WHERE ss.show_id = ?
				  AND r.user_id = ?
				  AND r.status = 'CONFIRMED'
				  AND ss.status = 'CONFIRMED'
				""", Long.class, showId, userId);

		return count == null ? 0 : count;
	}

	private List<String> findSeatsByReservationId(String reservationId) {

		return jdbcTemplate.query("""
				SELECT seat_number
				FROM show_seats
				WHERE reservation_id = ?
				ORDER BY seat_number
				""", (rs, rowNum) -> rs.getString("seat_number"), reservationId);
	}

	private List<String> normalizeSeats(List<String> requestedSeats) {

		if (requestedSeats == null || requestedSeats.isEmpty()) {
			throw new IllegalArgumentException("At least one seat is required");
		}

		List<String> seats = requestedSeats.stream().map(String::trim).toList();

		if (seats.stream().anyMatch(String::isBlank)) {
			throw new IllegalArgumentException("Seat number cannot be blank");
		}

		long uniqueCount = seats.stream().distinct().count();

		if (uniqueCount != seats.size()) {
			throw new IllegalArgumentException("Duplicate seats are not allowed");
		}

		return seats.stream().sorted().toList();
	}

	private String createRequestHash(Long showId, List<String> seats) {
		String canonical = showId + "|" + String.join(",", seats);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder();
			for (byte b : hash) {
				result.append(String.format("%02x", b));
			}
			return result.toString();
		} catch (Exception e) {
			throw new IllegalStateException("Unable to generate request hash", e);
		}
	}

	private ReservationResponse toResponse(Reservation reservation, List<String> seats) {

		return new ReservationResponse(reservation.getId(), reservation.getShow().getId(), reservation.getUserId(),
				seats, reservation.getAmountPaise(), reservation.getStatus().name());
	}

	private record IdempotencyRecord(String requestHash, String reservationId) {
	}
	
	
	@Transactional
	public ReservationResponse cancel(String reservationId, String userId) {

		Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
				.orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + reservationId));

		if (!reservation.getUserId().equals(userId)) {
			throw new ReservationOwnershipException("You are not allowed to cancel this reservation");
		}

		// Lock all seats belonging to this reservation
		List<ShowSeat> seatEntities = showSeatRepository.findByReservationIdForUpdate(reservationId);

		List<String> seats = seatEntities.stream().map(ShowSeat::getSeatNumber).toList();

		// Idempotent cancellation
		if (reservation.getStatus() == ReservationStatus.CANCELLED) {

			return new ReservationResponse(reservation.getId(), reservation.getShow().getId(), reservation.getUserId(),
					seats, reservation.getAmountPaise(), "CANCELLED");
		}

		// Release seats
		jdbcTemplate.update("""
				UPDATE show_seats
				SET status = 'AVAILABLE',
				    reservation_id = NULL
				WHERE reservation_id = ?
				  AND status = 'CONFIRMED'
				""", reservationId);

		// Mark reservation cancelled
		reservation.setStatus(ReservationStatus.CANCELLED);
		reservationRepository.save(reservation);

		log.info("reservation_cancelled reservationId={} showId={} userId={}", reservationId,
				reservation.getShow().getId(), userId);

		return new ReservationResponse(reservation.getId(), reservation.getShow().getId(), reservation.getUserId(),
				seats, reservation.getAmountPaise(), "CANCELLED");
	}
}
