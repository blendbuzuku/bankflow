package com.bankflow.accountservice.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "individual_clients")
@PrimaryKeyJoinColumn(name = "client_id")
public class IndividualClient extends Client {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    /**
     * Required now rather than optional.
     *
     * It is how two customers with the same name are told apart, what the age
     * check runs on, and one of the three identifiers the funds transfer rules
     * accept as travelling with a payment.
     */
    @Column(nullable = false)
    private LocalDate dateOfBirth;

    /*
     * Place of birth completes the cheapest identifier a payment can carry.
     * Where an address is unavailable, date and place of birth together are
     * accepted in its stead -- and unlike an address they never change.
     */
    @Column(name = "place_of_birth", length = 35)
    private String placeOfBirth;

    @Column(name = "country_of_birth", length = 2)
    private String countryOfBirth;

    /** ISO 3166-1 alpha-2. Both matter, and they are frequently different. */
    @Column(length = 2)
    private String nationality;

    @Column(name = "country_of_residence", length = 2)
    private String countryOfResidence;

    /*
     * The document that was actually examined. Without it "know your
     * customer" is a form somebody filled in about themselves.
     *
     * The expiry is held because an expired document does not identify
     * anybody, and the point of recording it is to know when to ask again.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "identity_document_type", length = 30)
    private IdentityDocumentType identityDocumentType;

    @Column(name = "identity_document_number", length = 40)
    private String identityDocumentNumber;

    @Column(name = "identity_document_country", length = 2)
    private String identityDocumentCountry;

    @Column(name = "identity_document_expiry")
    private LocalDate identityDocumentExpiry;

    /** The Kosovo personal number, where the customer has one. */
    @Column(name = "personal_number", length = 20)
    private String personalNumber;

    public IndividualClient() {
        setClientType(ClientType.INDIVIDUAL);
    }

    @Override
    public String getDisplayName() {
        return (firstName + " " + lastName).trim();
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
}