package com.bankflow.transactionservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedUser getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null ||
                !(authentication.getPrincipal()
                        instanceof AuthenticatedUser user)) {

            throw new IllegalStateException(
                    "Authenticated user not available"
            );
        }

        return user;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().userId();
    }

    public static boolean hasRole(String role) {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return authentication != null &&
                authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority.getAuthority()
                                        .equals("ROLE_" + role)
                        );
    }

    /**
     * Bank staff may act on any account. Customers may only act on their own.
     */
    public static boolean isStaff() {

        return hasRole("TELLER") ||
                hasRole("OPERATIONS") ||
                hasRole("BANK_ADMIN");
    }
}
