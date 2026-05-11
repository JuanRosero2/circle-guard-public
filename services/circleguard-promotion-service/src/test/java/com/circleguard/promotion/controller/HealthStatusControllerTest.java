package com.circleguard.promotion.controller;

import com.circleguard.promotion.service.HealthStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthStatusController.class)
@Import(com.circleguard.promotion.security.SecurityConfig.class)
class HealthStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthStatusService statusService;

    @MockBean
    private com.circleguard.promotion.security.JwtAuthenticationFilter jwtAuthFilter;

    @org.junit.jupiter.api.BeforeEach
    void setup() throws Exception {
        Mockito.doNothing().when(jwtAuthFilter).doFilter(
            Mockito.any(), 
            Mockito.any(), 
            Mockito.any()
        );
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void confirmPositive_WithPermission_CallsUpdateStatus() throws Exception {
        String json = "{\"anonymousId\": \"user-1\"}";

        // Configurar el mock para que no haga nada cuando se llame updateStatus
        doNothing().when(statusService).updateStatus("user-1", "CONFIRMED");

        mockMvc.perform(post("/api/v1/health/confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        verify(statusService).updateStatus("user-1", "CONFIRMED");
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void resolve_WithPermission_CallsResolveStatus() throws Exception {
        String json = "{\"anonymousId\": \"user-1\"}";

        // Configurar el mock para que no haga nada cuando se llame resolveStatus
        doNothing().when(statusService).resolveStatus("user-1", false);

        mockMvc.perform(post("/api/v1/health/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        verify(statusService).resolveStatus("user-1", false);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void resolve_WithoutPermission_Returns403() throws Exception {
        String json = "{\"anonymousId\": \"user-1\"}";

        mockMvc.perform(post("/api/v1/health/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolve_Unauthenticated_Returns401() throws Exception {
        String json = "{\"anonymousId\": \"user-1\"}";

        mockMvc.perform(post("/api/v1/health/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
    }
}
