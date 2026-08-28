package com.bankflow.accountservice.repository;

import com.bankflow.accountservice.entity.ClientSignatory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientSignatoryRepository
        extends JpaRepository<ClientSignatory, Long> {

    /** Which client this login acts for, if any. */
    Optional<ClientSignatory> findByUserId(Long userId);

    List<ClientSignatory> findByClientIdOrderByAddedAtAsc(Long clientId);

    boolean existsByClientIdAndUserId(Long clientId, Long userId);
}
