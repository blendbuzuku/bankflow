package com.bankflow.transactionservice.repository;

import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> accountId(Long accountId) {

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.or(
                        criteriaBuilder.equal(
                                root.get("sourceAccountId"),
                                accountId
                        ),
                        criteriaBuilder.equal(
                                root.get("destinationAccountId"),
                                accountId
                        )
                );
    }

    public static Specification<Transaction> status(
            TransactionStatus status) {

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("status"),
                        status
                );
    }

    public static Specification<Transaction> transactionType(
            TransactionType transactionType) {

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("transactionType"),
                        transactionType
                );
    }

    public static Specification<Transaction> bookingDateFrom(
            LocalDate fromDate) {

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(
                        root.get("bookingDate"),
                        fromDate
                );
    }

    public static Specification<Transaction> bookingDateTo(
            LocalDate toDate) {

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(
                        root.get("bookingDate"),
                        toDate
                );
    }
}