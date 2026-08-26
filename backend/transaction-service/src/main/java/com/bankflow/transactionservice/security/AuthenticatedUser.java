package com.bankflow.transactionservice.security;

public record AuthenticatedUser(
        Long userId,
        String username
) {

    /**
     * Reserved user ID for the service principal.
     *
     * Service-to-service tokens are not issued to a human user, but the
     * authentication filter still requires a userId claim. This sentinel
     * marks a token that represents transaction-service itself.
     */
    public static final Long SERVICE_USER_ID = 0L;

    public boolean isService() {
        return SERVICE_USER_ID.equals(userId);
    }
}
