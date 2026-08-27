package com.bankflow.transactionservice.directory;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * A bank we can send money to, by BIC.
 *
 * Kept as data rather than left to whoever is filling in the form. A BIC typed
 * by hand is a BIC typed wrong sooner or later, and the payment that carries it
 * either fails schema validation or — worse — reaches the wrong institution.
 * Choosing from a list the bank maintains removes the whole class of mistake.
 *
 * Entries are deactivated, never deleted: payments already sent quote the BIC,
 * and the directory has to be able to explain one that is no longer in use.
 */
@Entity
@Table(name = "correspondent_banks")
public class CorrespondentBank {

    /**
     * ISO 9362: four letters for the institution, two for the country, two
     * alphanumerics for the location, and optionally three more for a branch.
     */
    public static final String BIC_PATTERN =
            "^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "A BIC is required")
    @Pattern(
            regexp = BIC_PATTERN,
            message = "A BIC is 8 or 11 characters: 4 letters, 2 country "
                    + "letters, 2 for the location, and optionally 3 for a branch"
    )
    @Column(nullable = false, unique = true, length = 11)
    private String bic;

    @NotBlank(message = "The bank's name is required")
    @Size(max = 140, message = "The name must be 140 characters or fewer")
    @Column(nullable = false, length = 140)
    private String name;

    /** ISO 3166 alpha-2, and always the two letters inside the BIC. */
    @Column(nullable = false, length = 2)
    private String country;

    /**
     * Whether it may be chosen for a new payment. A deactivated bank still
     * explains the BIC on payments already sent.
     */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** How the bank reads in a dropdown. */
    public String getLabel() {
        return name + " — " + bic;
    }

    // --- accessors ---

    public Long getId() {
        return id;
    }

    public String getBic() {
        return bic;
    }

    public void setBic(String bic) {
        this.bic = bic == null ? null : bic.trim().toUpperCase();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country == null ? null : country.trim().toUpperCase();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
