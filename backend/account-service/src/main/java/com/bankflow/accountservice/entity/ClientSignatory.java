package com.bankflow.accountservice.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Somebody besides the registrant who may act for a client.
 *
 * A company is not a person and cannot sign in. Until now the model gave each
 * client exactly one login, which quietly meant a company had exactly one
 * human who could see its money or instruct a payment -- and made four-eyes
 * approval on a corporate account impossible, because the second pair of eyes
 * had nowhere to come from.
 *
 * Individuals get one of these too, created when they register, so there is a
 * single way of asking "which client does this login act for" rather than one
 * rule for people and another for companies.
 */
@Entity
@Table(
        name = "client_signatories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_client_signatory_user",
                columnNames = "user_id"
        )
)
public class ClientSignatory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /**
     * The login that may act.
     *
     * Unique across the table: a person may sign for one client, not several.
     * Somebody who genuinely acts for two companies needs two logins, which is
     * the right answer -- it keeps their actions attributable to the company
     * they were acting for at the time.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 50)
    private String username;

    /**
     * What this person may do.
     *
     * A signatory can instruct payments; a viewer can only look. The
     * distinction matters most on a company account, where a bookkeeper often
     * needs to see the statements and must not be able to move money.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignatoryAuthority authority;

    /**
     * Whether this is the person the client record was opened by.
     *
     * Kept so the original registrant can be told from people added later:
     * they are who the bank spoke to, and they cannot be removed without
     * leaving the client with nobody.
     */
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "added_by_username", length = 50)
    private String addedByUsername;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public SignatoryAuthority getAuthority() {
        return authority;
    }

    public void setAuthority(SignatoryAuthority authority) {
        this.authority = authority;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public String getAddedByUsername() {
        return addedByUsername;
    }

    public void setAddedByUsername(String addedByUsername) {
        this.addedByUsername = addedByUsername;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }
}
