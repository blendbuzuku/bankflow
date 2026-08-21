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

        String legalName,
        String registrationNumber,
        String taxNumber,
        String industry,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}