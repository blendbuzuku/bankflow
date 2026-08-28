package com.bankflow.accountservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "clients")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientType clientType;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    /*
     * Where the client is. Held on the base class because a company has a
     * registered address for exactly the same reasons a person has a home one:
     * it is what a payment message carries, what sanctions screening runs
     * against, and what post goes to.
     *
     * This is not paperwork. The scheme's pacs.008 defines PstlAdr for the
     * debtor, and without an address there is nothing to put in it.
     */
    @Column(name = "address_line1", length = 70)
    private String addressLine1;

    @Column(name = "address_line2", length = 70)
    private String addressLine2;

    @Column(length = 35)
    private String city;

    @Column(name = "postal_code", length = 16)
    private String postalCode;

    /** ISO 3166-1 alpha-2, which is what the scheme's Ctry element takes. */
    @Column(length = 2)
    private String country;

    /**
     * Whether this client holds or held public office, or is close to somebody
     * who does.
     *
     * Not a judgement about them. It raises the level of scrutiny their
     * payments get, and the regime requires it to be asked and recorded rather
     * than inferred.
     */
    @Column(name = "politically_exposed", nullable = false)
    private boolean politicallyExposed;

    @Column(name = "pep_details", length = 200)
    private String pepDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_of_funds", length = 30)
    private SourceOfFunds sourceOfFunds;

    @Column(name = "source_of_funds_detail", length = 200)
    private String sourceOfFundsDetail;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Client() {
    }

    /**
     * How this client is named on a payment.
     *
     * Polymorphic rather than an {@code instanceof} check at the call site:
     * accounts hold their client lazily, and a lazy proxy is of the base type,
     * so {@code instanceof IndividualClient} would be false even for an
     * individual. A method call goes through the proxy to the real instance.
     */
    public abstract String getDisplayName();

    public boolean isActive() {
        return ClientStatus.ACTIVE.name().equals(status);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (status == null) {
            status = ClientStatus.PENDING.name();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}