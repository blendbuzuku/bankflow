package com.bankflow.transactionservice.controller;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.pacs.MessageIdGenerator;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import com.bankflow.transactionservice.statement.Camt053Builder;
import com.bankflow.transactionservice.statement.Statement;
import com.bankflow.transactionservice.statement.StatementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Account statements, as data and as camt.053.
 *
 * Staff may pull a statement for any account; a customer only for one they
 * hold. That check is done here against the account's real owner rather than
 * trusted from the request, because an account id is trivially guessable and a
 * statement is a complete record of somebody's financial life.
 */
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService statementService;
    private final Camt053Builder camt053Builder;
    private final AccountClient accountClient;
    private final MessageIdGenerator messageIdGenerator;

    public StatementController(
            StatementService statementService,
            Camt053Builder camt053Builder,
            AccountClient accountClient,
            MessageIdGenerator messageIdGenerator) {

        this.statementService = statementService;
        this.camt053Builder = camt053Builder;
        this.accountClient = accountClient;
        this.messageIdGenerator = messageIdGenerator;
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<Statement> statement(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        requireAccess(accountId);

        return ResponseEntity.ok(statementService.forAccount(accountId, from, to));
    }

    /** The same statement in the scheme's own form. */
    @GetMapping(
            value = "/account/{accountId}/camt053",
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> camt053(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        requireAccess(accountId);

        Statement statement = statementService.forAccount(accountId, from, to);

        return ResponseEntity.ok(
                camt053Builder.build(messageIdGenerator.newMessageId(), statement)
        );
    }

    /**
     * Staff see any account; a customer only their own.
     *
     * The owning user is read from the account service rather than taken on
     * trust, so guessing an id gets a refusal instead of somebody else's
     * statement.
     */
    private void requireAccess(Long accountId) {

        if (SecurityUtils.hasRole("TELLER")
                || SecurityUtils.hasRole("OPERATIONS")
                || SecurityUtils.hasRole("BANK_ADMIN")) {
            return;
        }

        AuthenticatedUser user = SecurityUtils.getCurrentUser();

        List<AccountResponse> own = accountClient.accountsForUser(user.userId());

        boolean theirs = own.stream()
                .anyMatch(account -> account.id().equals(accountId));

        if (!theirs) {
            throw new AccessDeniedException(
                    "That account is not yours to see a statement for"
            );
        }
    }

    /** A period that runs backwards is a mistake, not an empty statement. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badPeriod(IllegalArgumentException exception) {
        throw new BusinessException(exception.getMessage());
    }
}
