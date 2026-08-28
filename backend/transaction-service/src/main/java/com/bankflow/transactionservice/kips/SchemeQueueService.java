package com.bankflow.transactionservice.kips;

import com.bankflow.transactionservice.entity.PaymentDirection;
import com.bankflow.transactionservice.entity.Transaction;
import com.bankflow.transactionservice.entity.TransactionStatus;
import com.bankflow.transactionservice.recall.RecallDirection;
import com.bankflow.transactionservice.recall.RecallRequest;
import com.bankflow.transactionservice.recall.RecallRequestRepository;
import com.bankflow.transactionservice.recall.RecallStatus;
import com.bankflow.transactionservice.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the other side of the scheme still owes us an answer on.
 *
 * There is no live KIPS link here, so somebody has to play the counterparty.
 * Doing that from the payment screen meant hunting for the payment first —
 * this gathers the work the scheme has outstanding into one queue, which is
 * also closer to how it really looks: the counterparty does not browse our
 * payments, it works through the ones addressed to it.
 */
@Service
public class SchemeQueueService {

    private final TransactionRepository transactionRepository;
    private final RecallRequestRepository recallRepository;

    public SchemeQueueService(
            TransactionRepository transactionRepository,
            RecallRequestRepository recallRepository) {

        this.transactionRepository = transactionRepository;
        this.recallRepository = recallRepository;
    }

    /**
     * Payments we have sent that no status report has answered.
     *
     * The money has left the debtor and sits in suspense, so until the scheme
     * answers these are the bank's open exposure.
     */
    @Transactional(readOnly = true)
    public List<SchemeAction> awaitingStatus() {

        return outbound(TransactionStatus.SENT).stream()
                .map(transaction -> describe(transaction, null))
                .toList();
    }

    /**
     * Payments that settled and could still come back.
     *
     * A beneficiary bank can return a settled payment whether or not we asked
     * it to, so this is not simply the recall queue — but where a recall is
     * open it is attached, because that is the case where an answer is owed.
     */
    @Transactional(readOnly = true)
    public List<SchemeAction> returnable() {

        Map<String, RecallRequest> openRecalls = recallRepository
                .findByStatusOrderByCreatedAtAsc(RecallStatus.REQUESTED)
                .stream()
                .filter(recall -> recall.getDirection() == RecallDirection.OUTBOUND)
                .collect(Collectors.toMap(
                        RecallRequest::getTransactionReference,
                        Function.identity(),
                        (first, second) -> first
                ));

        return outbound(TransactionStatus.SETTLED).stream()
                .map(transaction -> describe(
                        transaction,
                        openRecalls.get(transaction.getTransactionReference())
                ))
                .toList();
    }

    /**
     * On-us payments never reach the scheme, so they are never its to answer.
     */
    private List<Transaction> outbound(TransactionStatus status) {

        return transactionRepository
                .findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .filter(t -> t.getDirection() == PaymentDirection.OUTBOUND)
                .filter(t -> t.getPaymentType() != null
                        && t.getPaymentType().schemaRail() != null)
                .toList();
    }

    private SchemeAction describe(Transaction transaction, RecallRequest recall) {

        return new SchemeAction(
                transaction.getTransactionReference(),
                transaction.getEndToEndId(),
                transaction.getAmount(),
                transaction.getCurrency() == null
                        ? null : transaction.getCurrency().name(),
                transaction.getPaymentType().schemaRail(),
                transaction.getCreditorName(),
                transaction.getCreditorIban(),
                transaction.getCreditorAgentBic(),
                transaction.getCreatedAt(),
                recall == null ? null : recall.getCancellationId(),
                recall == null || recall.getReasonCode() == null
                        ? null : recall.getReasonCode().getCode()
        );
    }
}
