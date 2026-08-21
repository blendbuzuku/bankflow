package com.bankflow.accountservice.service;

import com.bankflow.accountservice.entity.*;
import com.bankflow.accountservice.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public void postEntries(
            Transaction transaction,
            List<LedgerEntry> entries) {

        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "A transaction must contain ledger entries"
            );
        }

        BigDecimal totalDebits = entries.stream()
                .filter(entry -> entry.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(entry -> entry.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalStateException(
                    "Ledger is not balanced. Debits: "
                            + totalDebits
                            + ", Credits: "
                            + totalCredits
            );
        }

        for (LedgerEntry entry : entries) {

            entry.setTransaction(transaction);

            if (entry.getAmount() == null
                    || entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {

                throw new IllegalArgumentException(
                        "Ledger entry amount must be greater than zero"
                );
            }

            if (entry.getCurrency() != transaction.getCurrency()) {

                throw new IllegalArgumentException(
                        "Ledger entry currency must match transaction currency"
                );
            }
        }

        ledgerEntryRepository.saveAll(entries);
    }
}