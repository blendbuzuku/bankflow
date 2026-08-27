package com.bankflow.transactionservice.eod;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DayCloseRepository extends JpaRepository<DayClose, Long> {

    Optional<DayClose> findByBookingDate(LocalDate bookingDate);

    boolean existsByBookingDate(LocalDate bookingDate);

    /** The recent run of days, newest first, for the history strip. */
    List<DayClose> findAllByOrderByBookingDateDesc(
            org.springframework.data.domain.Pageable page
    );
}
