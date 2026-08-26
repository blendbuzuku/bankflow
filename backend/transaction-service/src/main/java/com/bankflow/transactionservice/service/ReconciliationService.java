package com.bankflow.transactionservice.service;

import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.entity.AuditEventType;
import com.bankflow.transactionservice.entity.LedgerEntry;
import com.bankflow.transactionservice.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that the ledger describes what actually happened.
 *
 * The trial balance proves the ledger is internally consistent: debits equal
 * credits. It cannot prove the ledger is *complete*, because a movement that
 * never reached the ledger at all leaves both of its sides missing, and a
 * ledger missing both sides still nets to zero.
 *
 * That is not hypothetical. Balances live in account-service and commit
 * independently of this service's transaction, so a failure between the two
 * moves money and rolls back the ledger — and the trial balance reports
 * "balanced" while the books are wrong. This is the control that catches it.
 *
 * Entries and movements are matched on operation id, which both sides record,
 * so a break names the exact movement rather than only a wrong total.
 */
@Service
public class ReconciliationService {

    private static final Logger log =
            LoggerFactory.getLogger(ReconciliationService.class);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountClient accountClient;
    private final AuditService auditService;

    public ReconciliationService(
            LedgerEntryRepository ledgerEntryRepository,
            AccountClient accountClient,
            AuditService auditService) {

        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountClient = accountClient;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Reconciliation reconcile(LocalDate bookingDate) {

        Map<String, LedgerEntry> ledgerByOperation = new HashMap<>();
        int unverifiable = 0;

        for (LedgerEntry entry :
                ledgerEntryRepository.findByBookingDate(bookingDate)) {

            if (entry.getOperationId() == null) {
                /*
                 * Written before entries recorded which movement they caused.
                 * Reported separately rather than as a break, since we cannot
                 * tell whether it matched.
                 */
                unverifiable++;
                continue;
            }

            ledgerByOperation.put(entry.getOperationId(), entry);
        }

        Map<String, AccountClient.BalanceMovement> movementsByOperation =
                new HashMap<>();

        for (AccountClient.BalanceMovement movement :
                accountClient.movementsOn(bookingDate)) {

            movementsByOperation.put(movement.operationId(), movement);
        }

        List<Reconciliation.Break> unrecordedMoves = new ArrayList<>();
        List<Reconciliation.Break> unmatchedLedger = new ArrayList<>();
        List<Reconciliation.Break> amountMismatch = new ArrayList<>();

        int matched = 0;

        for (Map.Entry<String, AccountClient.BalanceMovement> moved
                : movementsByOperation.entrySet()) {

            LedgerEntry entry = ledgerByOperation.get(moved.getKey());
            AccountClient.BalanceMovement movement = moved.getValue();

            if (entry == null) {

                /*
                 * Money moved and the ledger knows nothing about it. This is
                 * the serious direction: the bank's records understate reality.
                 */
                unrecordedMoves.add(new Reconciliation.Break(
                        movement.operationId(),
                        movement.accountId(),
                        movement.operation(),
                        null,
                        movement.amount(),
                        "Balance moved with no ledger entry"
                ));

                continue;
            }

            if (entry.getAmount().compareTo(movement.amount()) != 0) {

                amountMismatch.add(new Reconciliation.Break(
                        movement.operationId(),
                        movement.accountId(),
                        movement.operation(),
                        entry.getAmount(),
                        movement.amount(),
                        "Ledger and balance movement disagree on amount"
                ));

                continue;
            }

            if (!entry.getEntryType().name().equals(movement.operation())) {

                amountMismatch.add(new Reconciliation.Break(
                        movement.operationId(),
                        movement.accountId(),
                        movement.operation(),
                        entry.getAmount(),
                        movement.amount(),
                        "Ledger says %s, balance moved %s".formatted(
                                entry.getEntryType(), movement.operation()
                        )
                ));

                continue;
            }

            matched++;
        }

        for (Map.Entry<String, LedgerEntry> booked
                : ledgerByOperation.entrySet()) {

            if (movementsByOperation.containsKey(booked.getKey())) {
                continue;
            }

            LedgerEntry entry = booked.getValue();

            /*
             * The ledger claims a movement that never happened. Less alarming
             * than the reverse but still wrong: the books overstate reality.
             */
            unmatchedLedger.add(new Reconciliation.Break(
                    booked.getKey(),
                    entry.getAccountId(),
                    entry.getEntryType().name(),
                    entry.getAmount(),
                    null,
                    "Ledger entry with no corresponding balance movement"
            ));
        }

        return Reconciliation.of(
                bookingDate,
                matched,
                unrecordedMoves,
                unmatchedLedger,
                amountMismatch,
                unverifiable
        );
    }

    /**
     * Reconciles and records the outcome, pass or fail.
     */
    @Transactional
    public Reconciliation runAndRecord(LocalDate bookingDate) {

        Reconciliation result = reconcile(bookingDate);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("bookingDate", bookingDate.toString());
        details.put("reconciled", result.reconciled());
        details.put("matched", result.matched());
        details.put("unverifiable", result.unverifiable());

        if (!result.reconciled()) {

            log.error("{}", result.describe());

            details.put("unrecordedMoves", describe(result.unrecordedMoves()));
            details.put("unmatchedLedger", describe(result.unmatchedLedger()));
            details.put("amountMismatch", describe(result.amountMismatch()));
        }

        auditService.record(
                AuditEventType.LEDGER_ENTRY_POSTED,
                null,
                "Reconciliation",
                bookingDate.toString(),
                result.describe(),
                details
        );

        return result;
    }

    private List<Map<String, Object>> describe(
            List<Reconciliation.Break> breaks) {

        return breaks.stream()
                .map(item -> {

                    Map<String, Object> line = new LinkedHashMap<>();

                    line.put("operationId", item.operationId());
                    line.put("accountId", item.accountId());
                    line.put("direction", item.direction());
                    line.put("ledgerAmount", item.ledgerAmount());
                    line.put("actualAmount", item.actualAmount());
                    line.put("detail", item.detail());

                    return line;
                })
                .toList();
    }
}
