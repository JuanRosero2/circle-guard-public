package com.circleguard.auth.e2e;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.security.SecurityConfig;
import com.circleguard.auth.controller.LoginController;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E Test: Full login flow.
 * Simulates a student logging in and receiving a JWT token with anonymousId.
 * Tests the complete request-response cycle including security filters.
 */
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LoginFlowE2ETest {

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
    void studentShouldLoginAndReceiveTokenWithAnonymousId() throws Exception {
        String username = "student001";
        UUID anonymousId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String jwtToken = "eyJhbGciOiJIUzI1NiJ9.mock-payload.mock-signature";

        var authToken = new UsernamePasswordAuthenticationToken(username, "pass",
                List.of(new SimpleGrantedAuthority("STUDENT")));
        Mockito.when(authManager.authenticate(Mockito.any())).thenReturn(authToken);
        Mockito.when(identityClient.getAnonymousId(username)).thenReturn(anonymousId);
        Mockito.when(jwtService.generateToken(Mockito.eq(anonymousId), Mockito.any())).thenReturn(jwtToken);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"student001\", \"password\": \"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(jwtToken))
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

    @Test
    void loginWithWrongPasswordShouldReturn401() throws Exception {
        Mockito.when(authManager.authenticate(Mockito.any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"baduser\", \"password\": \"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
