package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtTokenService.
 * Validates JWT generation, subject embedding, and permissions handling.
 */
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private final String secret = "my-super-secret-test-key-32-chars-long!!"; // 40 chars
    private final long expiration = 3600000L;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(secret, expiration);
    }

    @Test
    void shouldGenerateTokenWithCorrectAnonymousIdAsSubject() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = mock(Authentication.class);
        when(auth.getAuthorities()).thenReturn(List.of());

        String token = jwtTokenService.generateToken(anonymousId, auth);

        assertNotNull(token, "Generated token must not be null");

        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject(),
                "Token subject must match anonymousId");
    }

    @Test
    void shouldIncludePermissionsInTokenClaims() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = mock(Authentication.class);

        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("STUDENT"),
                new SimpleGrantedAuthority("HEALTH_CENTER")
        );
        doReturn(authorities).when(auth).getAuthorities();

        String token = jwtTokenService.generateToken(anonymousId, auth);

        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");
        assertNotNull(permissions, "Permissions claim must be present");
        assertTrue(permissions.contains("STUDENT"), "Should contain STUDENT permission");
        assertTrue(permissions.contains("HEALTH_CENTER"), "Should contain HEALTH_CENTER permission");
    }

    @Test
    void shouldGenerateTokenWithFutureExpiration() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = mock(Authentication.class);
        when(auth.getAuthorities()).thenReturn(List.of());

        long beforeGeneration = System.currentTimeMillis();
        String token = jwtTokenService.generateToken(anonymousId, auth);

        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        long tolerance = 5000; // 5 seconds tolerance for slow CI
        assertTrue(claims.getExpiration().getTime() >= (beforeGeneration + expiration) - tolerance,
                "Token expiration must be approximately consistent with the configured expiration");
    }
}
