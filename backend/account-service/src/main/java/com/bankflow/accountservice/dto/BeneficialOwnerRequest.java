package com.bankflow.accountservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One human being declared as owning or controlling a company. */
public class BeneficialOwnerRequest {

    @NotBlank(message = "A beneficial owner needs a name")
    @Size(max = 140)
    private String fullName;

    @NotNull(message = "A beneficial owner needs a date of birth")
    @Past(message = "A date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 2)
    private String nationality;

    @Size(max = 2)
    private String countryOfResidence;

    @NotNull(message = "State what share of the company this person holds")
    private BigDecimal ownershipPercentage;

    private boolean controlsByOtherMeans;

    private boolean politicallyExposed;

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
