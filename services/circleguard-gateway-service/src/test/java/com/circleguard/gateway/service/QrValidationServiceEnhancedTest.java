package com.circleguard.gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QrValidationServiceEnhancedTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    @InjectMocks
    private QrValidationService qrValidationService;
    
    private String testToken;
    private UUID testAnonymousId;
    private Key signingKey;

    @BeforeEach
    void setUp() {
        String qrSecret = "test-qr-secret-key-for-validation-purposes-only";
        ReflectionTestUtils.setField(qrValidationService, "qrSecret", qrSecret);
        
        testAnonymousId = UUID.randomUUID();
        signingKey = Keys.hmacShaKeyFor(qrSecret.getBytes());
        
        // Generate valid test token
        testToken = Jwts.builder()
                .setSubject(testAnonymousId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();
                
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void validateToken_ValidTokenWithHealthyStatus_ReturnsGreenAccess() {
        // Arrange
        when(valueOperations.get("user:status:" + testAnonymousId.toString())).thenReturn("HEALTHY");

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(testToken);

        // Assert
        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
        assertEquals("Welcome to Campus", result.message());
        
        verify(redisTemplate).opsForValue();
        verify(valueOperations).get("user:status:" + testAnonymousId.toString());
    }

    @Test
    void validateToken_ValidTokenWithContagiedStatus_ReturnsRedAccess() {
        // Arrange
        when(valueOperations.get("user:status:" + testAnonymousId.toString())).thenReturn("CONTAGIED");

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(testToken);

        // Assert
        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Health Risk Detected", result.message());
        
        verify(redisTemplate).opsForValue();
        verify(valueOperations).get("user:status:" + testAnonymousId.toString());
    }

    @Test
    void validateToken_ValidTokenWithPotentialStatus_ReturnsRedAccess() {
        // Arrange
        when(valueOperations.get("user:status:" + testAnonymousId.toString())).thenReturn("POTENTIAL");

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(testToken);

        // Assert
        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Health Risk Detected", result.message());
        
        verify(redisTemplate).opsForValue();
        verify(valueOperations).get("user:status:" + testAnonymousId.toString());
    }

    @Test
    void validateToken_ValidTokenWithNoStatusInRedis_ReturnsGreenAccess() {
        // Arrange
        when(valueOperations.get("user:status:" + testAnonymousId.toString())).thenReturn(null);

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(testToken);

        // Assert
        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
        assertEquals("Welcome to Campus", result.message());
        
        verify(redisTemplate).opsForValue();
        verify(valueOperations).get("user:status:" + testAnonymousId.toString());
    }

    @Test
    void validateToken_InvalidToken_ReturnsRedAccess() {
        // Arrange
        String invalidToken = "invalid.token.here";

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(invalidToken);

        // Assert
        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }

    @Test
    void validateToken_ExpiredToken_ReturnsRedAccess() {
        // Arrange
        String expiredToken = Jwts.builder()
                .setSubject(testAnonymousId.toString())
                .setIssuedAt(new Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
                .setExpiration(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .signWith(signingKey)
                .compact();

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(expiredToken);

        // Assert
        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }

    @Test
    void validateToken_TokenWithWrongSignature_ReturnsRedAccess() {
        // Arrange
        Key wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-that-is-long-enough-to-be-secure".getBytes());
        String wrongSignatureToken = Jwts.builder()
                .setSubject(testAnonymousId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(wrongKey)
                .compact();

        // Act
        QrValidationService.ValidationResult result = qrValidationService.validateToken(wrongSignatureToken);

        // Assert
        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }
}
