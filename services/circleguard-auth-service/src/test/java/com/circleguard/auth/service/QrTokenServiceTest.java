package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QrTokenService.
 * Validates QR token generation and content structure.
 */
class QrTokenServiceTest {

    private QrTokenService qrTokenService;
    private final String secret = "my-qr-secret-key-for-test-1234567890!!"; // pad to 40 chars
    private final long expiration = 300000L; // 5 minutes

    @BeforeEach
    void setUp() {
        qrTokenService = new QrTokenService(secret, expiration);
    }

    @Test
    void shouldGenerateQrTokenForValidUser() {
        UUID anonymousId = UUID.randomUUID();

        String token = qrTokenService.generateQrToken(anonymousId);

        assertNotNull(token, "QR token must not be null");
        assertFalse(token.isBlank(), "QR token must not be blank");
        // Standard JWT format: 3 parts separated by dots
        assertEquals(3, token.split("\\.").length, "QR token must have JWT format (3 parts)");
    }

    @Test
    void shouldEmbedAnonymousIdAsSubjectInQrToken() {
        UUID anonymousId = UUID.randomUUID();

        String token = qrTokenService.generateQrToken(anonymousId);

        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject(),
                "QR token subject must match the anonymousId");
    }

    @Test
    void shouldGenerateExpiredQrTokenWhenExpirationIsZero() {
        // Create service with 0ms expiration (immediately expired)
        QrTokenService expiredService = new QrTokenService(secret, 0L);
        UUID anonymousId = UUID.randomUUID();

        String token = expiredService.generateQrToken(anonymousId);

        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        assertThrows(ExpiredJwtException.class, () ->
                Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token),
                "Parsing an immediately-expired token should throw ExpiredJwtException"
        );
    }
}
