package com.paytm.seatreservation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paytm.seatreservation.entity.IdempotencyKey;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
	
	   Optional<IdempotencyKey> findByUserIdAndShowIdAndIdempotencyKey(
	            String userId,
	            Long showId,
	            String idempotencyKey
	    );
}
