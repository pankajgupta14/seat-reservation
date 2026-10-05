package com.paytm.seatreservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paytm.seatreservation.dto.BookingLock;

public interface BookingLockRepository extends JpaRepository<BookingLock, Long> {

}
