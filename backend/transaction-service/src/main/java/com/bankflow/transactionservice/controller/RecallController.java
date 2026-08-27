package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.pacs.ReasonCode;
import com.bankflow.transactionservice.recall.RecallDirection;
import com.bankflow.transactionservice.recall.RecallRequest;
import com.bankflow.transactionservice.recall.RecallStatus;
import com.bankflow.transactionservice.service.PaymentReturnService;
import com.bankflow.transactionservice.service.RecallService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Getting a settled payment back, in both directions.
 *
 * Asking is a teller's job; answering somebody else's request is not. Agreeing
 * to a recall takes money out of a customer's account, which is the same weight
 * of decision as releasing a parked payment, so it sits with the same role.
 */
@RestController
@RequestMapping("/api/recalls")
public class RecallController {

    private final RecallService recallService;
    private final PaymentReturnService returnService;

    public RecallController(
            RecallService recallService,
            PaymentReturnService returnService) {

        this.recallService = recallService;
        this.returnService = returnService;
    }

    /** What a recall looks like on the wire. */
    public record RecallResponse(
            Long id,
            String cancellationId,
            String transactionReference,
            RecallDirection direction,
            RecallStatus status,
            String reasonCode,
            String reasonDescription,
            String additionalInformation,
            String requestedByUsername,
            String decidedByUsername,
            LocalDateTime decidedAt,
            String decisionNote,
            LocalDateTime createdAt) {

        static RecallResponse from(RecallRequest request) {

            return new RecallResponse(
                    request.getId(),
                    request.getCancellationId(),
                    request.getTransactionReference(),
                    request.getDirection(),
                    request.getStatus(),
                    request.getReasonCode().getCode(),
                    request.getReasonCode().getDescription(),
                    request.getAdditionalInformation(),
                    request.getRequestedByUsername(),
                    request.getDecidedByUsername(),
                    request.getDecidedAt(),
                    request.getDecisionNote(),
                    request.getCreatedAt()
            );
        }
    }

    public record RecallRequestBody(
            @NotBlank(message = "A transaction reference is required")
            String transactionReference,
            String reasonCode,
            String note) {
    }

    public record DecisionBody(String note) {
    }

    public record ReturnBody(
            @NotBlank(message = "A transaction reference is required")
            String transactionReference,
            String reasonCode,
            String note) {
    }

    /** Everything waiting for someone to decide. */
    @GetMapping
    public ResponseEntity<List<RecallResponse>> awaitingDecision() {

        return ResponseEntity.ok(
                recallService.awaitingDecision().stream()
                        .map(RecallResponse::from)
                        .toList()
        );
    }

    @GetMapping("/transaction/{transactionReference}")
    public ResponseEntity<List<RecallResponse>> forTransaction(
            @PathVariable String transactionReference) {

        return ResponseEntity.ok(
                recallService.forTransaction(transactionReference).stream()
                        .map(RecallResponse::from)
                        .toList()
        );
    }

    /** Ask the beneficiary's bank to send one of our payments back. */
    @PostMapping
    public ResponseEntity<RecallResponse> request(
            @RequestBody RecallRequestBody body) {

        RecallRequest created = recallService.requestRecall(
                body.transactionReference(),
                reasonOf(body.reasonCode(), ReasonCode.CUST),
                body.note()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(RecallResponse.from(created));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<RecallResponse> accept(
            @PathVariable Long id,
            @RequestBody(required = false) DecisionBody body) {

        return ResponseEntity.ok(
                RecallResponse.from(
                        recallService.accept(id, body == null ? null : body.note())
                )
        );
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RecallResponse> reject(
            @PathVariable Long id,
            @RequestBody(required = false) DecisionBody body) {

        return ResponseEntity.ok(
                RecallResponse.from(
                        recallService.reject(id, body == null ? null : body.note())
                )
        );
    }

    /**
     * Sends back a payment that reached us, without waiting to be asked —
     * a beneficiary who refuses the money, or an account found to be wrong
     * after it was credited.
     */
    @PostMapping("/returns")
    public ResponseEntity<Void> returnInbound(@RequestBody ReturnBody body) {

        returnService.returnInboundPayment(
                body.transactionReference(),
                reasonOf(body.reasonCode(), ReasonCode.AC01),
                body.note()
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * An unrecognised code falls back rather than failing: the reason is
     * explanatory, and refusing the whole action over it would help nobody.
     */
    private ReasonCode reasonOf(String code, ReasonCode fallback) {

        if (code == null || code.isBlank()) {
            return fallback;
        }

        try {
            return ReasonCode.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }
}
