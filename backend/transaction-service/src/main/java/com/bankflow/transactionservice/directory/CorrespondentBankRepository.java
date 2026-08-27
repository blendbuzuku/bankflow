package com.bankflow.transactionservice.directory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorrespondentBankRepository
        extends JpaRepository<CorrespondentBank, Long> {

    Optional<CorrespondentBank> findByBic(String bic);

    boolean existsByBic(String bic);

    /** What may be chosen for a new payment. */
    List<CorrespondentBank> findByActiveTrueOrderByNameAsc();

    List<CorrespondentBank> findAllByOrderByNameAsc();
}
