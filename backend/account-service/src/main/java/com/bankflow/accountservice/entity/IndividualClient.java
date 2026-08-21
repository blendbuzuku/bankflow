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

    private LocalDate dateOfBirth;

    public IndividualClient() {
        setClientType(ClientType.INDIVIDUAL);
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
}