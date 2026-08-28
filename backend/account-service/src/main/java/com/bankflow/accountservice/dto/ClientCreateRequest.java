package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.ClientType;
import com.bankflow.accountservice.entity.IdentityDocumentType;
import com.bankflow.accountservice.entity.LegalForm;
import com.bankflow.accountservice.entity.SourceOfFunds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ClientCreateRequest {

    @NotNull(message = "Client type is required")
    private ClientType clientType;

    // Individual fields

    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    // Business fields

    @Size(max = 150, message = "Legal name must not exceed 150 characters")
    private String legalName;

    @Size(max = 100, message = "Registration number must not exceed 100 characters")
    private String registrationNumber;

    @Size(max = 100, message = "Tax number must not exceed 100 characters")
    private String taxNumber;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    private String industry;

    // Common fields

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    public ClientCreateRequest() {
    }

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    // --- where they are -------------------------------------------------

    @Size(max = 70, message = "Address line must not exceed 70 characters")
    private String addressLine1;

    @Size(max = 70, message = "Address line must not exceed 70 characters")
    private String addressLine2;

    @Size(max = 35, message = "Town or city must not exceed 35 characters")
    private String city;

    @Size(max = 16, message = "Postal code must not exceed 16 characters")
    private String postalCode;

    /** ISO 3166-1 alpha-2, which is what the scheme's Ctry element takes. */
    @Size(max = 2)
    private String country;

    // --- who they are, for a person --------------------------------------

    @Size(max = 35)
    private String placeOfBirth;

    @Size(max = 2)
    private String countryOfBirth;

    @Size(max = 2)
    private String nationality;

    @Size(max = 2)
    private String countryOfResidence;

    private IdentityDocumentType identityDocumentType;

    @Size(max = 40)
    private String identityDocumentNumber;

    @Size(max = 2)
    private String identityDocumentCountry;

    private LocalDate identityDocumentExpiry;

    @Size(max = 20)
    private String personalNumber;

    // --- risk ------------------------------------------------------------

    private boolean politicallyExposed;

    @Size(max = 200)
    private String pepDetails;

    private SourceOfFunds sourceOfFunds;

    @Size(max = 200)
    private String sourceOfFundsDetail;

    // --- what the company is ---------------------------------------------

    private LegalForm legalForm;

    private LocalDate dateOfIncorporation;

    @Size(max = 8)
    private String naceCode;

    @Valid
    private List<BeneficialOwnerRequest> beneficialOwners = new ArrayList<>();

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPlaceOfBirth() {
        return placeOfBirth;
    }

    public void setPlaceOfBirth(String placeOfBirth) {
        this.placeOfBirth = placeOfBirth;
    }

    public String getCountryOfBirth() {
        return countryOfBirth;
    }

    public void setCountryOfBirth(String countryOfBirth) {
        this.countryOfBirth = countryOfBirth;
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

    public IdentityDocumentType getIdentityDocumentType() {
        return identityDocumentType;
    }

    public void setIdentityDocumentType(IdentityDocumentType identityDocumentType) {
        this.identityDocumentType = identityDocumentType;
    }

    public String getIdentityDocumentNumber() {
        return identityDocumentNumber;
    }

    public void setIdentityDocumentNumber(String identityDocumentNumber) {
        this.identityDocumentNumber = identityDocumentNumber;
    }

    public String getIdentityDocumentCountry() {
        return identityDocumentCountry;
    }

    public void setIdentityDocumentCountry(String identityDocumentCountry) {
        this.identityDocumentCountry = identityDocumentCountry;
    }

    public LocalDate getIdentityDocumentExpiry() {
        return identityDocumentExpiry;
    }

    public void setIdentityDocumentExpiry(LocalDate identityDocumentExpiry) {
        this.identityDocumentExpiry = identityDocumentExpiry;
    }

    public String getPersonalNumber() {
        return personalNumber;
    }

    public void setPersonalNumber(String personalNumber) {
        this.personalNumber = personalNumber;
    }

    public boolean isPoliticallyExposed() {
        return politicallyExposed;
    }

    public void setPoliticallyExposed(boolean politicallyExposed) {
        this.politicallyExposed = politicallyExposed;
    }

    public String getPepDetails() {
        return pepDetails;
    }

    public void setPepDetails(String pepDetails) {
        this.pepDetails = pepDetails;
    }

    public SourceOfFunds getSourceOfFunds() {
        return sourceOfFunds;
    }

    public void setSourceOfFunds(SourceOfFunds sourceOfFunds) {
        this.sourceOfFunds = sourceOfFunds;
    }

    public String getSourceOfFundsDetail() {
        return sourceOfFundsDetail;
    }

    public void setSourceOfFundsDetail(String sourceOfFundsDetail) {
        this.sourceOfFundsDetail = sourceOfFundsDetail;
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

    public List<BeneficialOwnerRequest> getBeneficialOwners() {
        return beneficialOwners;
    }

    public void setBeneficialOwners(List<BeneficialOwnerRequest> beneficialOwners) {
        this.beneficialOwners =
                beneficialOwners == null ? new ArrayList<>() : beneficialOwners;
    }
}
