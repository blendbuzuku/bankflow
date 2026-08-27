package com.bankflow.transactionservice.pacs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** The most recent traffic, newest first, for the message inspector. */
    List<PacsMessage> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable page
    );

    /**
     * Finds messages by whatever the person happens to know.
     *
     * The searchable facts — an IBAN, a name, an amount — live inside the XML
     * rather than in columns of their own, so the body is searched too. That is
     * a scan, and deliberately so: extracting them into columns would mean
     * re-parsing every message ever stored to backfill, and the honest scan is
     * correct today where a half-populated column would quietly miss older
     * traffic.
     *
     * A blank term matches everything, which is what an empty search box should
     * do.
     */
    @Query("""
            SELECT m
            FROM PacsMessage m
            WHERE (:term IS NULL
                   OR LOWER(m.messageId)            LIKE :term
                   OR LOWER(m.transactionReference) LIKE :term
                   OR LOWER(m.endToEndId)           LIKE :term
                   OR LOWER(m.statusCode)           LIKE :term
                   OR LOWER(m.reasonCode)           LIKE :term
                   OR LOWER(m.rawXml)               LIKE :term)
              AND (:type IS NULL OR m.messageType = :type)
              AND (:direction IS NULL OR m.direction = :direction)
            ORDER BY m.createdAt DESC
            """)
    List<PacsMessage> search(
            @Param("term") String term,
            @Param("type") PacsMessageType type,
            @Param("direction") MessageDirection direction,
            org.springframework.data.domain.Pageable page
    );
}
