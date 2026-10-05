package com.paytm.seatreservation.dto;

import com.paytm.seatreservation.entity.Show;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "booking_locks",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_booking_lock",
            columnNames = {"show_id", "user_id"}
        )
    }
)
public class BookingLock {

	    @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;

	    @ManyToOne(fetch = FetchType.LAZY, optional = false)
	    @JoinColumn(name = "show_id", nullable = false)
	    private Show show;

	    @Column(name = "user_id", nullable = false, length = 100)
	    private String userId;

	    public Long getId() {
	        return id;
	    }

	    public Show getShow() {
	        return show;
	    }

	    public void setShow(Show show) {
	        this.show = show;
	    }

	    public String getUserId() {
	        return userId;
	    }

	    public void setUserId(String userId) {
	        this.userId = userId;
	    }
}
