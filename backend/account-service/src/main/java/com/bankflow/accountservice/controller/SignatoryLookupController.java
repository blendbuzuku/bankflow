package com.bankflow.accountservice.controller;

import com.bankflow.accountservice.entity.ClientSignatory;
import com.bankflow.accountservice.repository.ClientSignatoryRepository;
import com.bankflow.accountservice.entity.SignatoryAuthority;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a login is allowed to do for the client it acts for.
 *
 * Separate from the maintenance endpoints because the payment engine asks this
 * on every customer instruction and does so as a service, not as a person. It
 * is a lookup, not an administrative action.
 */
@RestController
@RequestMapping("/api/signatories")
public class SignatoryLookupController {

    private final ClientSignatoryRepository signatoryRepository;

    public SignatoryLookupController(
            ClientSignatoryRepository signatoryRepository) {

        this.signatoryRepository = signatoryRepository;
    }

    /**
     * Answers with NOT_FOUND when the login acts for nobody, which is a
     * meaningful answer rather than an error: plenty of logins belong to staff
     * and have no client behind them at all.
     */
    @PreAuthorize(
            "hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN', 'TRANSACTION_SERVICE')"
    )
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<Authority> forUser(@PathVariable Long userId) {

        return signatoryRepository.findByUserId(userId)
                .map(signatory -> ResponseEntity.ok(
                        new Authority(
                                signatory.getClientId(),
                                signatory.getAuthority()
                        )
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record Authority(Long clientId, SignatoryAuthority authority) {
    }
}
