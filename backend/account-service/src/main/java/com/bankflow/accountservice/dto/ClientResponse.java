package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.ClientType;
import com.bankflow.accountservice.entity.IdentityDocumentType;
import com.bankflow.accountservice.entity.LegalForm;
import com.bankflow.accountservice.entity.SourceOfFunds;
import java.util.List;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClientResponse(
        Long id,
        ClientType clientType,

        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,

        String status,

        /**
         * The bank's own legal entity, which owns the suspense and income
         * accounts. It is not a customer and must never be offered as one.
         */
        boolean internal,

        String legalName,
        String registrationNumber,
        String taxNumber,
        String industry,

        // --- where they are, and what identifies them ------------------

        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String country,

        String placeOfBirth,
        String countryOfBirth,
        String nationality,
        String countryOfResidence,

        IdentityDocumentType identityDocumentType,

        /**
         * Shown as its last characters only.
         *
         * Enough for a teller to confirm the document in front of them is the
         * one on file, and not enough to be worth stealing from a screen.
         */
        String identityDocumentNumber,

        String identityDocumentCountry,
        LocalDate identityDocumentExpiry,

        boolean politicallyExposed,
        String pepDetails,
        SourceOfFunds sourceOfFunds,

        LegalForm legalForm,
        LocalDate dateOfIncorporation,
        String naceCode,
        List<BeneficialOwnerResponse> beneficialOwners,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** One declared owner, as shown back. */
    public record BeneficialOwnerResponse(
            Long id,
            String fullName,
            LocalDate dateOfBirth,
            String nationality,
            String countryOfResidence,
            java.math.BigDecimal ownershipPercentage,
            boolean controlsByOtherMeans,
            boolean politicallyExposed
    ) {
    }
}