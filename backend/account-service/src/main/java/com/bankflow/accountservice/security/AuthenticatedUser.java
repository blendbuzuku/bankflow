package com.bankflow.accountservice.security;

public record AuthenticatedUser(
        Long userId,
        String username
) {
}