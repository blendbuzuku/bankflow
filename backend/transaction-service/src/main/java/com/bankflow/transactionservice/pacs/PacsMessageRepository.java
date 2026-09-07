package com.bankflow.transactionservice.pacs;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * The scheme message archive.
 *
 * Backed by MongoDB rather than the ledger's database. Nothing here joins to a
 * payment -- messages are found by the identifiers the scheme itself uses, and
 * a message may legitimately concern a payment we do not hold.
 */
public interface PacsMessageRepository
        extends MongoRepository<PacsMessage, String>, PacsMessageSearch {

    Optional<PacsMessage> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);

    /** Every message concerning one payment, oldest first. */
    List<PacsMessage> findByTransactionReferenceOrderByCreatedAtAsc(
            String transactionReference
    );

    /** Status reports answering a given message. */
    List<PacsMessage> findByRelatedMessageId(String relatedMessageId);

    List<PacsMessage> findByEndToEndId(String endToEndId);

    /** The most recent traffic, newest first, for the message inspector. */
    List<PacsMessage> findAllByOrderByCreatedAtDesc(Pageable page);
}
