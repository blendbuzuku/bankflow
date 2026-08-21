package com.bankflow.authservice.dto;

import com.bankflow.authservice.entity.UserRole;
import com.bankflow.authservice.entity.UserStatus;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}