package com.circleguard.auth.integration;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import com.circleguard.auth.controller.LoginController;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: Auth service → Identity client communication.
 * Validates that login triggers identity lookup to obtain anonymousId.
 */
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class AuthToIdentityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authManager;

    @MockBean
    private JwtTokenService jwtService;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void shouldCallIdentityServiceToObtainAnonymousIdAfterSuccessfulLogin() throws Exception {
        String username = "student1";
        String password = "pass123";
        UUID anonymousId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String expectedToken = "mock-jwt-token-for-student1";

        Authentication auth = new UsernamePasswordAuthenticationToken(username, password,
                List.of(new SimpleGrantedAuthority("STUDENT")));

        Mockito.when(authManager.authenticate(Mockito.any()))
                .thenReturn(auth);
        Mockito.when(identityClient.getAnonymousId(username))
                .thenReturn(anonymousId);
        Mockito.when(jwtService.generateToken(Mockito.eq(anonymousId), Mockito.any()))
                .thenReturn(expectedToken);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"student1\", \"password\": \"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(expectedToken))
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()))
                .andExpect(jsonPath("$.type").value("Bearer"));

        // Verify identity service was called exactly once
        Mockito.verify(identityClient, Mockito.times(1)).getAnonymousId(username);
    }

    @Test
    void shouldReturn403WhenAuthenticationFails() throws Exception {
        Mockito.when(authManager.authenticate(Mockito.any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"baduser\", \"password\": \"wrongpass\"}"))
                .andExpect(status().isUnauthorized());
    }
}
