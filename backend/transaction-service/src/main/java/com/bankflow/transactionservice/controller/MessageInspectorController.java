package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.pacs.PacsMessage;
import com.bankflow.transactionservice.pacs.PacsMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.bankflow.transactionservice.pacs.MessageDirection;
import com.bankflow.transactionservice.pacs.PacsMessageType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every scheme message the bank has exchanged, in one place.
 *
 * The per-payment view answers "what happened to this payment". This answers
 * the other question — "what has been going in and out" — which is what
 * somebody looking into a scheme problem actually starts from, before they
 * know which payment is at fault.
 *
 * Staff only. A message quotes both parties' names, accounts and amounts, so
 * the traffic log is the whole bank's business rather than any one customer's.
 */
@RestController
@RequestMapping("/api/messages")
@PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
public class MessageInspectorController {

    /** Enough to see the day without pulling the entire history. */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final PacsMessageRepository repository;

    public MessageInspectorController(PacsMessageRepository repository) {
        this.repository = repository;
    }

    /** A message in the list, without its body. */
    public record MessageSummary(
            String messageId,
            String messageType,
            String description,
            String direction,
            String status,
            String transactionReference,
            String endToEndId,
            String statusCode,
            String reasonCode,
            int sizeBytes,
            LocalDateTime createdAt) {

        static MessageSummary from(PacsMessage message) {

            return new MessageSummary(
                    message.getMessageId(),
                    message.getMessageType().name(),
                    message.getMessageType().getDescription(),
                    message.getDirection().name(),
                    message.getStatus().name(),
                    message.getTransactionReference(),
                    message.getEndToEndId(),
                    message.getStatusCode(),
                    message.getReasonCode(),
                    message.getRawXml() == null ? 0 : message.getRawXml().length(),
                    message.getCreatedAt()
            );
        }
    }

    /**
     * Finds messages by whatever the person knows.
     *
     * Nobody looking into a problem starts from a message identifier. They
     * start from an IBAN, a name, an amount or a customer's complaint, so all
     * of those match — the body is searched as well as the metadata.
     *
     * @param q         an IBAN, name, amount, reference or code. Blank returns
     *                  the most recent traffic.
     * @param type      one definition only, e.g. PACS_008
     * @param direction OUTBOUND or INBOUND
     */
    @GetMapping
    public ResponseEntity<List<MessageSummary>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PacsMessageType type,
            @RequestParam(required = false) MessageDirection direction,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {

        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));

        /*
         * Passed as typed. The percent signs this used to wrap it in were SQL
         * LIKE wildcards; the archive matches on a regex now, where they would
         * be literal characters nobody has in a reference.
         */
        String term = q;

        return ResponseEntity.ok(
                repository
                        .search(term, type, direction, PageRequest.of(0, capped))
                        .stream()
                        .map(MessageSummary::from)
                        .toList()
        );
    }

    /**
     * The definitions actually present, each with what it means.
     *
     * Built from the traffic rather than from the enum: a filter offering a
     * message type this bank has never exchanged is a filter that returns
     * nothing, which teaches the user only that the screen is broken.
     */
    @GetMapping("/types")
    public ResponseEntity<List<MessageTypeOption>> types() {

        Map<PacsMessageType, Long> counts = repository.findAll().stream()
                .collect(Collectors.groupingBy(
                        PacsMessage::getMessageType, Collectors.counting()));

        return ResponseEntity.ok(
                counts.entrySet().stream()
                        .map(entry -> new MessageTypeOption(
                                entry.getKey().name(),
                                entry.getKey().getIdentifier(
                                        entry.getKey().isSupportedOn("ach")
                                                ? "ach" : "rtgs"),
                                entry.getKey().getDescription(),
                                entry.getValue()
                        ))
                        .sorted(Comparator.comparing(MessageTypeOption::identifier))
                        .toList()
        );
    }

    /** A definition, what it is called, and what it means. */
    public record MessageTypeOption(
            String name,
            String identifier,
            String description,
            long count) {
    }
}
