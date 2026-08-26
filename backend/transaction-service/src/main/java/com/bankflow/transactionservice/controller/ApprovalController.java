package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.dto.TransactionResponse;
import com.bankflow.transactionservice.service.ApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The approval queue.
 *
 * Tellers can see what is waiting so they know their payment has not been
 * forgotten, but only operations and administrators can release or decline one,
 * and never a payment they created themselves.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> awaitingApproval() {

        return ResponseEntity.ok(
                approvalService.awaitingApproval()
                        .stream()
                        .map(TransactionResponse::fromEntity)
                        .toList()
        );
    }

    @PostMapping("/{transactionReference}/approve")
    public ResponseEntity<TransactionResponse> approve(
            @PathVariable String transactionReference) {

        return ResponseEntity.ok(
                TransactionResponse.fromEntity(
                        approvalService.approve(transactionReference)
                )
        );
    }

    @PostMapping("/{transactionReference}/decline")
    public ResponseEntity<TransactionResponse> decline(
            @PathVariable String transactionReference,
            @RequestParam(required = false) String reason) {

        return ResponseEntity.ok(
                TransactionResponse.fromEntity(
                        approvalService.decline(transactionReference, reason)
                )
        );
    }
}
