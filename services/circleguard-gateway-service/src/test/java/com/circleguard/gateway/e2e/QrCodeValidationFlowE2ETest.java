package com.circleguard.gateway.e2e;

import com.circleguard.gateway.controller.GateController;
import com.circleguard.gateway.service.QrValidationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Key;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E Test: QR code validation flow.
 * Simulates a guard scanning a student's QR code at a building entrance.
 * Tests the full HTTP cycle: QR token → validation → access granted/denied response.
 */
@WebMvcTest(GateController.class)
class QrCodeValidationFlowE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QrValidationService validationService;

    private static final String QR_SECRET = "my-super-secret-test-key-for-e2e!";

    @Test
    void studentWithGreenStatusShouldBeAllowedThrough() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(QR_SECRET.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        when(validationService.validateToken(token))
                .thenReturn(new QrValidationService.ValidationResult(true, "GREEN", anonymousId));

        mockMvc.perform(post("/api/v1/gate/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("GREEN"))
                .andExpect(jsonPath("$.anonymousId").value(anonymousId));
    }

    @Test
    void studentWithRedStatusShouldBeDeniedAccess() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(QR_SECRET.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        when(validationService.validateToken(token))
                .thenReturn(new QrValidationService.ValidationResult(false, "RED", anonymousId));

        mockMvc.perform(post("/api/v1/gate/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("RED"));
    }
}
