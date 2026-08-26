package com.bankflow.accountservice.dto;

import com.bankflow.accountservice.entity.ClientType;

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

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}