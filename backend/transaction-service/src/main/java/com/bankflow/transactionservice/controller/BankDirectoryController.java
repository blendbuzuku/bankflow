package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.directory.BankDirectoryService;
import com.bankflow.transactionservice.directory.CorrespondentBank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The directory of banks that can be paid.
 *
 * The selectable list is open to anyone signed in — it is what fills the
 * dropdown on a payment form, and a customer needs it as much as a teller.
 * Everything that changes it is restricted at the service.
 */
@RestController
@RequestMapping("/api/banks")
public class BankDirectoryController {

    private final BankDirectoryService directory;

    public BankDirectoryController(BankDirectoryService directory) {
        this.directory = directory;
    }

    public record BankResponse(
            Long id,
            String bic,
            String name,
            String country,
            String label,
            boolean active) {

        static BankResponse from(CorrespondentBank bank) {

            return new BankResponse(
                    bank.getId(),
                    bank.getBic(),
                    bank.getName(),
                    bank.getCountry(),
                    bank.getLabel(),
                    bank.isActive()
            );
        }
    }

    public record AddBankRequest(
            @NotBlank(message = "A BIC is required")
            @Pattern(
                    regexp = CorrespondentBank.BIC_PATTERN,
                    message = "A BIC is 8 or 11 characters, e.g. TEBKXKPR"
            )
            String bic,

            @NotBlank(message = "The bank's name is required")
            String name,

            String country) {
    }

    public record RenameRequest(@NotBlank String name) {
    }

    /** Banks that may be chosen for a payment. */
    @GetMapping
    public ResponseEntity<List<BankResponse>> selectable() {

        return ResponseEntity.ok(
                directory.selectable().stream().map(BankResponse::from).toList()
        );
    }

    /** Everything, retired entries included. */
    @GetMapping("/all")
    public ResponseEntity<List<BankResponse>> all() {

        return ResponseEntity.ok(
                directory.all().stream().map(BankResponse::from).toList()
        );
    }

    @PostMapping
    public ResponseEntity<BankResponse> add(
            @org.springframework.web.bind.annotation.RequestBody
            @jakarta.validation.Valid AddBankRequest request) {

        CorrespondentBank added = directory.add(
                request.bic(), request.name(), request.country()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BankResponse.from(added));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BankResponse> rename(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody
            @jakarta.validation.Valid RenameRequest request) {

        return ResponseEntity.ok(
                BankResponse.from(directory.rename(id, request.name()))
        );
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<BankResponse> retire(@PathVariable Long id) {
        return ResponseEntity.ok(BankResponse.from(directory.setActive(id, false)));
    }

    @PostMapping("/{id}/reinstate")
    public ResponseEntity<BankResponse> reinstate(@PathVariable Long id) {
        return ResponseEntity.ok(BankResponse.from(directory.setActive(id, true)));
    }
}
