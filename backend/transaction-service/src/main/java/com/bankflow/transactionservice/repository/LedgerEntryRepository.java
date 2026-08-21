package com.bankflow.transactionservice.repository;

import com.bankflow.transactionservice.entity.LedgerEntry;
import com.bankflow.transactionservice.entity.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(
            Long transactionId
    );

    List<LedgerEntry> findByAccountId(
            Long accountId
    );

    List<LedgerEntry> findByAccountIdAndEntryType(
            Long accountId,
            LedgerEntryType entryType
    );

    boolean existsByEntryReference(
            String entryReference
    );
}