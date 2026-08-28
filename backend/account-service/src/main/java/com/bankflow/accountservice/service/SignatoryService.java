package com.bankflow.accountservice.service;

import com.bankflow.accountservice.audit.AuditService;
import com.bankflow.accountservice.entity.Client;
import com.bankflow.accountservice.entity.ClientSignatory;
import com.bankflow.accountservice.entity.SignatoryAuthority;
import com.bankflow.accountservice.repository.ClientRepository;
import com.bankflow.accountservice.repository.ClientSignatoryRepository;
import com.bankflow.accountservice.security.AuthenticatedUser;
import com.bankflow.accountservice.security.SecurityUtils;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.common.exception.BusinessException;
import com.bankflow.common.exception.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Who may act for a client.
 *
 * The single place that answers "which client does this login belong to",
 * replacing a lookup on clients.user_id that could only ever return one login
 * per client. That was right for a person and wrong for a company, where the
 * whole point of a corporate account is that several named people can operate
 * it under agreed limits.
 */
@Service
public class SignatoryService {

    private final ClientSignatoryRepository signatoryRepository;
    private final ClientRepository clientRepository;
    private final AuditService auditService;

    public SignatoryService(
            ClientSignatoryRepository signatoryRepository,
            ClientRepository clientRepository,
            AuditService auditService) {

        this.signatoryRepository = signatoryRepository;
        this.clientRepository = clientRepository;
        this.auditService = auditService;
    }

    /** The client this login acts for, whoever added them. */
    @Transactional(readOnly = true)
    public Optional<Long> clientIdFor(Long userId) {

        return signatoryRepository.findByUserId(userId)
                .map(ClientSignatory::getClientId);
    }

    /**
     * Whether this login may move money, as opposed to only look at it.
     *
     * Asked before a payment rather than at the screen, because a screen that
     * hides a button is a convenience and this is a control.
     */
    @Transactional(readOnly = true)
    public boolean maySignFor(Long userId) {

        return signatoryRepository.findByUserId(userId)
                .map(signatory ->
                        signatory.getAuthority() == SignatoryAuthority.SIGNATORY)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<ClientSignatory> forClient(Long clientId) {
        return signatoryRepository.findByClientIdOrderByAddedAtAsc(clientId);
    }

    /**
     * Records the person a client record was opened by.
     *
     * Called as part of registration so that an individual has a row here too:
     * one rule for resolving a login rather than a special case for people and
     * another for companies.
     */
    @Transactional
    public ClientSignatory recordRegistrant(Long clientId, Long userId) {

        ClientSignatory signatory = new ClientSignatory();

        signatory.setClientId(clientId);
        signatory.setUserId(userId);
        signatory.setAuthority(SignatoryAuthority.SIGNATORY);
        signatory.setPrimary(true);

        return signatoryRepository.save(signatory);
    }

    /**
     * Adds somebody who may act for a client.
     *
     * Staff only. A signatory can move a company's money, so who holds that
     * authority is the bank's decision to record rather than something an
     * existing signatory may grant themselves -- otherwise one compromised
     * login quietly becomes several.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public ClientSignatory add(
            Long clientId,
            Long userId,
            String username,
            SignatoryAuthority authority) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + clientId
                ));

        if (userId == null) {
            throw new BusinessException("Name the login that will act");
        }

        if (authority == null) {
            throw new BusinessException(
                    "Say whether this person may instruct payments or only "
                            + "look at the accounts"
            );
        }

        signatoryRepository.findByUserId(userId).ifPresent(existing -> {

            if (existing.getClientId().equals(clientId)) {
                throw new BusinessException(
                        "That login already acts for this client"
                );
            }

            throw new BusinessException(
                    ("That login already acts for another client. A person who "
                            + "genuinely acts for two needs a separate login "
                            + "for each, so what they do stays attributable to "
                            + "the right one.")
            );
        });

        ClientSignatory signatory = new ClientSignatory();

        signatory.setClientId(clientId);
        signatory.setUserId(userId);
        signatory.setUsername(username);
        signatory.setAuthority(authority);
        signatory.setPrimary(false);

        AuthenticatedUser actor = SecurityUtils.getCurrentUser();
        signatory.setAddedByUsername(actor == null ? null : actor.username());

        ClientSignatory saved = signatoryRepository.save(signatory);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("clientId", clientId);
        details.put("userId", userId);
        details.put("username", username);
        details.put("authority", authority.name());

        auditService.record(
                AuditEventType.SIGNATORY_ADDED,
                "Client",
                String.valueOf(clientId),
                "%s may now act for %s as %s".formatted(
                        username == null ? "user " + userId : username,
                        client.getDisplayName(),
                        authority == SignatoryAuthority.SIGNATORY
                                ? "a signatory" : "a viewer"
                ),
                details
        );

        return saved;
    }

    /**
     * Removes somebody's authority to act.
     *
     * The registrant cannot be removed. They are who the bank onboarded, and
     * removing them would leave a client nobody can operate and no obvious way
     * to put that right.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public void remove(Long clientId, Long signatoryId) {

        ClientSignatory signatory = signatoryRepository.findById(signatoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No signatory with id " + signatoryId
                ));

        if (!signatory.getClientId().equals(clientId)) {
            throw new BusinessException(
                    "That signatory does not act for this client"
            );
        }

        if (signatory.isPrimary()) {
            throw new BusinessException(
                    "This is the person the client was registered by, and "
                            + "removing them would leave nobody able to "
                            + "operate the account"
            );
        }

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("clientId", clientId);
        details.put("userId", signatory.getUserId());
        details.put("username", signatory.getUsername());
        details.put("authority", signatory.getAuthority().name());

        signatoryRepository.delete(signatory);

        auditService.record(
                AuditEventType.SIGNATORY_REMOVED,
                "Client",
                String.valueOf(clientId),
                "%s may no longer act for this client".formatted(
                        signatory.getUsername() == null
                                ? "user " + signatory.getUserId()
                                : signatory.getUsername()
                ),
                details
        );
    }
}
