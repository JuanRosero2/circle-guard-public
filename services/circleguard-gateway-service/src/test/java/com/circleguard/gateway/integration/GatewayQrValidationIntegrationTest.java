package com.circleguard.gateway.integration;

import com.circleguard.gateway.service.QrValidationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import com.circleguard.gateway.controller.GateController;

import java.security.Key;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: Gateway controller + QrValidationService + Redis lookup.
 * Validates the full QR validation flow from HTTP request to Redis status lookup.
 */
@WebMvcTest(GateController.class)
class GatewayQrValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QrValidationService validationService;

    private static final String QR_SECRET = "my-super-secret-test-key-32-chars-long";

    @Test
    void shouldReturnValidAndGreenStatusForClearUser() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        QrValidationService.ValidationResult result = new QrValidationService.ValidationResult(true, "GREEN", anonymousId);

        Key key = Keys.hmacShaKeyFor(QR_SECRET.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        when(validationService.validateToken(token)).thenReturn(result);

        mockMvc.perform(post("/api/v1/gate/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("GREEN"));
    }

    @Test
    void shouldReturnInvalidAndRedStatusForContagiousUser() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        QrValidationService.ValidationResult result = new QrValidationService.ValidationResult(false, "RED", anonymousId);

        Key key = Keys.hmacShaKeyFor(QR_SECRET.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        when(validationService.validateToken(token)).thenReturn(result);

        mockMvc.perform(post("/api/v1/gate/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("RED"));
    }

    @Test
    void shouldReturnInvalidForMalformedToken() throws Exception {
        String badToken = "this.is.not.a.valid.jwt";
        QrValidationService.ValidationResult result = new QrValidationService.ValidationResult(false, "INVALID", null);

        when(validationService.validateToken(badToken)).thenReturn(result);

        mockMvc.perform(post("/api/v1/gate/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + badToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }
}
