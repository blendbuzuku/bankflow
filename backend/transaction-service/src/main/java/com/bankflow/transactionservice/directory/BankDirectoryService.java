package com.bankflow.transactionservice.directory;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.common.exception.DuplicateResourceException;
import com.bankflow.common.exception.ResourceNotFoundException;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.transactionservice.security.AuthenticatedUser;
import com.bankflow.transactionservice.security.SecurityUtils;
import com.bankflow.transactionservice.service.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The banks this bank can pay.
 *
 * Reading is open to anyone signed in, because the list is what fills the
 * dropdown on a payment form and a customer needs it as much as a teller does.
 * Changing it is not: a wrong entry here misdirects every payment that picks
 * it, so maintaining the directory sits with operations.
 */
@Service
public class BankDirectoryService {

    private final CorrespondentBankRepository repository;
    private final AuditService auditService;

    public BankDirectoryService(
            CorrespondentBankRepository repository,
            AuditService auditService) {

        this.repository = repository;
        this.auditService = auditService;
    }

    /** What can be chosen for a payment right now. */
    public List<CorrespondentBank> selectable() {
        return repository.findByActiveTrueOrderByNameAsc();
    }

    /** Everything, including retired entries, for whoever maintains the list. */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    public List<CorrespondentBank> all() {
        return repository.findAllByOrderByNameAsc();
    }

    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public CorrespondentBank add(String bic, String name, String country) {

        CorrespondentBank bank = new CorrespondentBank();

        bank.setBic(bic);
        bank.setName(name);
        bank.setCountry(country);
        bank.setActive(true);

        requireConsistentCountry(bank);

        if (repository.existsByBic(bank.getBic())) {
            throw new DuplicateResourceException(
                    "%s is already in the directory".formatted(bank.getBic())
            );
        }

        CorrespondentBank saved = repository.save(bank);

        record(saved, "added", "Added %s to the bank directory".formatted(
                saved.getLabel()));

        return saved;
    }

    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public CorrespondentBank rename(Long id, String name) {

        CorrespondentBank bank = require(id);

        bank.setName(name);

        if (bank.getName() == null || bank.getName().isBlank()) {
            throw new BusinessException("The bank's name is required");
        }

        CorrespondentBank saved = repository.save(bank);

        record(saved, "renamed", "Renamed %s to %s".formatted(
                saved.getBic(), saved.getName()));

        return saved;
    }

    /**
     * Takes a bank out of use, or puts it back.
     *
     * Not a delete: payments already sent quote the BIC, and the directory has
     * to keep explaining one that is no longer offered.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public CorrespondentBank setActive(Long id, boolean active) {

        CorrespondentBank bank = require(id);

        if (bank.isActive() == active) {
            throw new BusinessException(
                    "%s is already %s".formatted(
                            bank.getBic(), active ? "in use" : "retired")
            );
        }

        bank.setActive(active);

        CorrespondentBank saved = repository.save(bank);

        record(saved, active ? "reinstated" : "retired",
                "%s %s".formatted(
                        saved.getLabel(),
                        active ? "is available again" : "is no longer offered"));

        return saved;
    }

    /**
     * The country in the BIC and the country on the record have to agree.
     * They are the same fact, and a disagreement means one of them is wrong.
     */
    private void requireConsistentCountry(CorrespondentBank bank) {

        if (bank.getBic() == null || bank.getBic().length() < 6) {
            return;
        }

        String fromBic = bank.getBic().substring(4, 6);

        if (bank.getCountry() == null || bank.getCountry().isBlank()) {
            bank.setCountry(fromBic);
            return;
        }

        if (!fromBic.equals(bank.getCountry())) {
            throw new BusinessException(
                    "%s says the bank is in %s, but %s was given".formatted(
                            bank.getBic(), fromBic, bank.getCountry())
            );
        }
    }

    private CorrespondentBank require(Long id) {

        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No bank in the directory with id " + id
                ));
    }

    private void record(CorrespondentBank bank, String action, String summary) {

        AuthenticatedUser actor = SecurityUtils.getCurrentUser();

        auditService.record(
                AuditEventType.BANK_DIRECTORY_CHANGED,
                null,
                "CorrespondentBank",
                bank.getBic(),
                summary + " (" + actor.username() + ")",
                Map.of(
                        "bic", bank.getBic(),
                        "name", bank.getName(),
                        "action", action,
                        "by", actor.username()
                )
        );
    }
}
