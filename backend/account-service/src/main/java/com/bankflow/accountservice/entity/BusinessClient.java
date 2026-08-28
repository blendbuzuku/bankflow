package com.bankflow.accountservice.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "business_clients")
@PrimaryKeyJoinColumn(name = "client_id")
public class BusinessClient extends Client {

    @Column(nullable = false)
    private String legalName;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    private String taxNumber;

    private String industry;

    /**
     * How the company is constituted.
     *
     * Decides who can bind it and how far the owners' liability runs, which is
     * why a bank records it rather than treating every company alike.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "legal_form", length = 30)
    private LegalForm legalForm;

    @Column(name = "date_of_incorporation")
    private LocalDate dateOfIncorporation;

    /** NACE rev. 2, so the sector is a code rather than somebody's phrasing. */
    @Column(name = "nace_code", length = 8)
    private String naceCode;

    /**
     * The people who actually own or control the company.
     *
     * The whole point of onboarding a company: an account is opened in a
     * company's name, but the risk belongs to the humans behind it, and a
     * shell exists precisely so that those two look different. Anything at or
     * above the disclosure threshold has to be named.
     */
    @OneToMany(
            mappedBy = "client",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<BeneficialOwner> beneficialOwners = new ArrayList<>();

    public BusinessClient() {
        setClientType(ClientType.BUSINESS);
    }

    @Override
    public String getDisplayName() {
        return legalName;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getTaxNumber() {
        return taxNumber;
    }

    public void setTaxNumber(String taxNumber) {
        this.taxNumber = taxNumber;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public LegalForm getLegalForm() {
        return legalForm;
    }

    public void setLegalForm(LegalForm legalForm) {
        this.legalForm = legalForm;
    }

    public LocalDate getDateOfIncorporation() {
        return dateOfIncorporation;
    }

    public void setDateOfIncorporation(LocalDate dateOfIncorporation) {
        this.dateOfIncorporation = dateOfIncorporation;
    }

    public String getNaceCode() {
        return naceCode;
    }

    public void setNaceCode(String naceCode) {
        this.naceCode = naceCode;
    }

    public List<BeneficialOwner> getBeneficialOwners() {
        return beneficialOwners;
    }

    /** Kept on both sides so the cascade writes the foreign key. */
    public void addBeneficialOwner(BeneficialOwner owner) {
        owner.setClient(this);
        beneficialOwners.add(owner);
    }
}