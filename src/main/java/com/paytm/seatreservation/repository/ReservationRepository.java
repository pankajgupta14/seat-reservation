package com.paytm.seatreservation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.paytm.seatreservation.entity.Reservation;

import jakarta.persistence.LockModeType;

@Repository
public interface ReservationRepository  extends JpaRepository<Reservation, String>{

	  @Query("""
	            SELECT COUNT(r)
	            FROM Reservation r
	            WHERE r.show.id = :showId
	              AND r.userId = :userId
	              AND r.status = com.paytm.seatreservation.enums.ReservationStatus.CONFIRMED
	            """)
	    long countConfirmedReservations(
	            Long showId,
	            String userId
	    );
	  
	  @Lock(LockModeType.PESSIMISTIC_WRITE)
	  @Query("SELECT r FROM Reservation r WHERE r.id = :id")
	  Optional<Reservation> findByIdForUpdate(@Param("id") String id);
}
