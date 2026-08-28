package com.bankflow.accountservice.controller;

import com.bankflow.accountservice.entity.ClientSignatory;
import com.bankflow.accountservice.entity.SignatoryAuthority;
import com.bankflow.accountservice.service.SignatoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Who may act for a client.
 *
 * Reading the list is open to any member of staff, since knowing who can
 * instruct on an account is part of servicing it. Changing it is not: a
 * signatory can move somebody else's money.
 */
@RestController
@RequestMapping("/api/clients/{clientId}/signatories")
public class SignatoryController {

    private final SignatoryService signatoryService;

    public SignatoryController(SignatoryService signatoryService) {
        this.signatoryService = signatoryService;
    }

    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    @GetMapping
    public ResponseEntity<List<SignatoryResponse>> list(@PathVariable Long clientId) {

        return ResponseEntity.ok(
                signatoryService.forClient(clientId)
                        .stream()
                        .map(SignatoryResponse::from)
                        .toList()
        );
    }

    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @PostMapping
    public ResponseEntity<SignatoryResponse> add(
            @PathVariable Long clientId,
            @RequestBody AddSignatoryRequest request) {

        return ResponseEntity.ok(
                SignatoryResponse.from(
                        signatoryService.add(
                                clientId,
                                request.userId(),
                                request.username(),
                                request.authority()
                        )
                )
        );
    }

    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @DeleteMapping("/{signatoryId}")
    public ResponseEntity<Void> remove(
            @PathVariable Long clientId,
            @PathVariable Long signatoryId) {

        signatoryService.remove(clientId, signatoryId);

        return ResponseEntity.noContent().build();
    }

    public record AuthorityResponse(Long clientId, SignatoryAuthority authority) {
    }

    public record AddSignatoryRequest(
            Long userId,
            String username,
            SignatoryAuthority authority
    ) {
    }

    public record SignatoryResponse(
            Long id,
            Long userId,
            String username,
            SignatoryAuthority authority,
            boolean primary,
            String addedByUsername,
            LocalDateTime addedAt
    ) {

        static SignatoryResponse from(ClientSignatory signatory) {

            return new SignatoryResponse(
                    signatory.getId(),
                    signatory.getUserId(),
                    signatory.getUsername(),
                    signatory.getAuthority(),
                    signatory.isPrimary(),
                    signatory.getAddedByUsername(),
                    signatory.getAddedAt()
            );
        }
    }
}
