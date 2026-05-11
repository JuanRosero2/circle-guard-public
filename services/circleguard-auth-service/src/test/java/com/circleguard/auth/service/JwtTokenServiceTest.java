package com.circleguard.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    
    @Mock
    private Authentication authentication;
    
    @Mock
    private GrantedAuthority grantedAuthority;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService("test-secret-key-for-testing-purposes-only", 3600000L);
    }

    @Test
    void generateToken_ValidAuthentication_ReturnsValidToken() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        when(authentication.getAuthorities()).thenReturn((Collection) List.of(grantedAuthority));
        when(grantedAuthority.getAuthority()).thenReturn("ROLE_USER");

        // Act
        String token = jwtTokenService.generateToken(anonymousId, authentication);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        // Verify token structure
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length); // Header, Payload, Signature
    }

    @Test
    void generateToken_WithNullAnonymousId_ThrowsException() {
        // Arrange
        when(authentication.getAuthorities()).thenReturn((Collection) List.of(grantedAuthority));
        when(grantedAuthority.getAuthority()).thenReturn("ROLE_USER");

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            jwtTokenService.generateToken(null, authentication);
        });
    }

    @Test
    void generateToken_WithMultipleAuthorities_IncludesAllPermissions() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        GrantedAuthority authority1 = org.mockito.Mockito.mock(GrantedAuthority.class);
        GrantedAuthority authority2 = org.mockito.Mockito.mock(GrantedAuthority.class);
        
        when(authentication.getAuthorities()).thenReturn((Collection) List.of(authority1, authority2));
        when(authority1.getAuthority()).thenReturn("ROLE_USER");
        when(authority2.getAuthority()).thenReturn("ROLE_ADMIN");

        // Act
        String token = jwtTokenService.generateToken(anonymousId, authentication);

        // Assert
        assertNotNull(token);
        
        // Parse token to verify claims
        var claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor("test-secret-key-for-testing-purposes-only".getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");
        assertEquals(2, permissions.size());
        assertTrue(permissions.contains("ROLE_USER"));
        assertTrue(permissions.contains("ROLE_ADMIN"));
    }

    @Test
    void generateToken_WithExpirationDate_SetsCorrectExpiration() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        when(authentication.getAuthorities()).thenReturn((Collection) List.of(grantedAuthority));
        when(grantedAuthority.getAuthority()).thenReturn("ROLE_USER");

        // Act
        long beforeGeneration = System.currentTimeMillis();
        String token = jwtTokenService.generateToken(anonymousId, authentication);
        long afterGeneration = System.currentTimeMillis();

        // Assert
        var claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor("test-secret-key-for-testing-purposes-only".getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        Date expiration = claims.getExpiration();
        long expectedExpiration = beforeGeneration + 3600000L;
        
        // Allow for small time differences (within 1 second)
        assertTrue(Math.abs(expiration.getTime() - expectedExpiration) < 1000);
    }

    @Test
    void generateToken_WithSubject_SetsCorrectSubject() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        when(authentication.getAuthorities()).thenReturn((Collection) List.of(grantedAuthority));
        when(grantedAuthority.getAuthority()).thenReturn("ROLE_USER");

        // Act
        String token = jwtTokenService.generateToken(anonymousId, authentication);

        // Assert
        var claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor("test-secret-key-for-testing-purposes-only".getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        assertEquals(anonymousId.toString(), claims.getSubject());
    }
}
