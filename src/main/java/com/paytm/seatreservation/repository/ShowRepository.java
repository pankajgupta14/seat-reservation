package com.paytm.seatreservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paytm.seatreservation.entity.Show;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long>{

}
