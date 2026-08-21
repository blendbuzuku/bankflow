package com.bankflow.accountservice.entity;

import jakarta.persistence.*;

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

    public BusinessClient() {
        setClientType(ClientType.BUSINESS);
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
}