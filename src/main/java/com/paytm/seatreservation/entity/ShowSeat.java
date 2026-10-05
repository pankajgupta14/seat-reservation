package com.paytm.seatreservation.entity;

import com.paytm.seatreservation.enums.SeatStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Table(
	    name = "show_seats",
	    uniqueConstraints = {
	        @UniqueConstraint(
	            name = "uk_show_seat",
	            columnNames = {"show_id", "seat_number"}
	        )
	    },
	    indexes = {
	        @Index(
	            name = "idx_show_seat_status",
	            columnList = "show_id,status"
	        ),
	        @Index(
	            name = "idx_show_seat_reservation_status",
	            columnList = "reservation_id,status"
	        )
	    }
	)
@Data
public class ShowSeat {

	    @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;

	    @ManyToOne(fetch = FetchType.LAZY, optional = false)
	    @JoinColumn(name = "show_id", nullable = false)
	    private Show show;

	    @Column(name = "seat_number", nullable = false, length = 50)
	    private String seatNumber;

	    @Enumerated(EnumType.STRING)
	    @Column(nullable = false, length = 20)
	    private SeatStatus status = SeatStatus.AVAILABLE;

	    @Column(name = "reservation_id", length = 100)
	    private String reservationId;
	    
}
