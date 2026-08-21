package com.bankflow.authservice.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn
) {
}