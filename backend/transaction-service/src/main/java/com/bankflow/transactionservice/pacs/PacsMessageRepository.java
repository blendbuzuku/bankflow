package com.bankflow.transactionservice.pacs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PacsMessageRepository extends JpaRepository<PacsMessage, Long> {

    Optional<PacsMessage> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);

    /** Every message concerning one payment, oldest first. */
    List<PacsMessage> findByTransactionReferenceOrderByCreatedAtAsc(
            String transactionReference
    );

    /** Status reports answering a given message. */
    List<PacsMessage> findByRelatedMessageId(String relatedMessageId);

    List<PacsMessage> findByEndToEndId(String endToEndId);
}
