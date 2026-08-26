package com.bankflow.transactionservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Mints short-lived tokens that identify transaction-service itself.
 *
 * Ledger calls into account-service must NOT reuse the caller's token.
 * A customer's token lacks the authority to move balances, and forwarding a
 * staff token would let the ledger operation inherit whatever authority that
 * particular human happened to hold. Instead the service authenticates as
 * itself, with the single role that permits balance operations.
 *
 * Authorising the *caller* is a separate concern, handled at this service's
 * own API boundary before any ledger call is made.
 */
@Component
public class ServiceTokenProvider {

    static final String SERVICE_ROLE = "TRANSACTION_SERVICE";

    private final SecretKey secretKey;
    private final String serviceName;
    private final long tokenTtlSeconds;

    public ServiceTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${bankflow.service.name:transaction-service}") String serviceName,
            @Value("${bankflow.service.token-ttl-seconds:60}") long tokenTtlSeconds) {

        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.serviceName = serviceName;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /**
     * A fresh token per call. These are cheap to mint and deliberately
     * short-lived, so there is nothing worth caching and nothing worth
     * stealing for long.
     */
    public String issueToken() {

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(serviceName)
                .claim("userId", AuthenticatedUser.SERVICE_USER_ID)
                .claim("role", SERVICE_ROLE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(tokenTtlSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public String issueAuthorizationHeader() {
        return "Bearer " + issueToken();
    }
}
