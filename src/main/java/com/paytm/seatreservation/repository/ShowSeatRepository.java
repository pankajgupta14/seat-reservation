package com.paytm.seatreservation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.paytm.seatreservation.entity.ShowSeat;

import jakarta.persistence.LockModeType;

@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long>{

	Optional<ShowSeat> findByShowIdAndSeatNumber(Long showId,String seatNumber);

     List<ShowSeat> findByShowId(Long showId);
     
     @Modifying
     @Query("""
             UPDATE ShowSeat s
             SET s.status = com.paytm.seatreservation.enums.SeatStatus.CONFIRMED,
                 s.reservationId = :reservationId
             WHERE s.show.id = :showId
               AND s.seatNumber = :seatNumber
               AND s.status = com.paytm.seatreservation.enums.SeatStatus.AVAILABLE
             """)
     int confirmSeat(
             @Param("showId") Long showId,
             @Param("seatNumber") String seatNumber,
             @Param("reservationId") String reservationId
     );
     
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("""
         SELECT s
         FROM ShowSeat s
         WHERE s.reservationId = :reservationId
         ORDER BY s.seatNumber
     """)
     List<ShowSeat> findByReservationIdForUpdate(
             @Param("reservationId") String reservationId);
}
