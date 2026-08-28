package com.bankflow.accountservice.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A human being who owns or controls a company that banks here.
 *
 * A company is a legal fiction and cannot itself be screened, sanctioned, or
 * held to account. Recording the people behind it is what makes the account
 * belong to somebody, and a structure designed to hide them is the exact thing
 * the disclosure exists to defeat.
 *
 * Ownership is a percentage rather than a flag because control is a matter of
 * degree, and because the sum across owners is itself informative: a company
 * whose declared owners account for a third of it has more to explain.
 */
@Entity
@Table(name = "beneficial_owners")
public class BeneficialOwner {

    /**
     * At or above this share of a company, a person must be named.
     *
     * The threshold in the EU anti-money-laundering directives, which Kosovo
     * mirrors. Below it somebody is an investor; at or above it they are in a
     * position to direct what the company does with its money.
     */
    public static final BigDecimal DISCLOSURE_THRESHOLD = new BigDecimal("25.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private BusinessClient client;

    @Column(name = "full_name", nullable = false, length = 140)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(length = 2)
    private String nationality;

    @Column(name = "country_of_residence", length = 2)
    private String countryOfResidence;

    /** Percentage of the company held, to two places. */
    @Column(name = "ownership_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal ownershipPercentage;

    /**
     * Whether control comes from something other than shares.
     *
     * Ownership is the common route but not the only one -- a person who can
     * appoint the board controls a company they may own none of, and leaving
     * them off the record because their percentage is zero would defeat the
     * purpose.
     */
    @Column(name = "controls_by_other_means", nullable = false)
    private boolean controlsByOtherMeans;

    @Column(name = "politically_exposed", nullable = false)
    private boolean politicallyExposed;

    public BeneficialOwner() {
    }

    public Long getId() {
        return id;
    }

    public BusinessClient getClient() {
        return client;
    }

    public void setClient(BusinessClient client) {
        this.client = client;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getCountryOfResidence() {
        return countryOfResidence;
    }

    public void setCountryOfResidence(String countryOfResidence) {
        this.countryOfResidence = countryOfResidence;
    }

    public BigDecimal getOwnershipPercentage() {
        return ownershipPercentage;
    }

    public void setOwnershipPercentage(BigDecimal ownershipPercentage) {
        this.ownershipPercentage = ownershipPercentage;
    }

    public boolean isControlsByOtherMeans() {
        return controlsByOtherMeans;
    }

    public void setControlsByOtherMeans(boolean controlsByOtherMeans) {
        this.controlsByOtherMeans = controlsByOtherMeans;
    }

    public boolean isPoliticallyExposed() {
        return politicallyExposed;
    }

    public void setPoliticallyExposed(boolean politicallyExposed) {
        this.politicallyExposed = politicallyExposed;
    }
}
