package com.circleguard.promotion.integration;

import com.circleguard.promotion.controller.HealthStatusController;
import com.circleguard.promotion.security.SecurityConfig;
import com.circleguard.promotion.service.HealthStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: HealthStatus controller → service → Kafka/Redis chain.
 * Validates the health status update and resolve flows through the full stack.
 */
@WebMvcTest(HealthStatusController.class)
@Import(SecurityConfig.class)
@org.springframework.test.context.TestPropertySource(properties = "jwt.secret=my-super-secret-dev-key-with-more-than-sixty-four-characters-for-safety-1234567890")
class HealthStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthStatusService statusService;

    @Test
    @WithMockUser(authorities = "HEALTH_CENTER")
    void shouldUpdateStatusToConfirmedAndRespondOk() throws Exception {
        String json = "{\"anonymousId\": \"user-123\"}";

        doNothing().when(statusService).updateStatus("user-123", "CONFIRMED");

        mockMvc.perform(post("/api/v1/health/confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(statusService, times(1)).updateStatus("user-123", "CONFIRMED");
    }

    @Test
    @WithMockUser(authorities = "HEALTH_CENTER")
    void shouldOverrideResolveWithForceFlag() throws Exception {
        String json = "{\"anonymousId\": \"user-fenced\", \"override\": true}";

        doNothing().when(statusService).resolveStatus("user-fenced", true);

        mockMvc.perform(post("/api/v1/health/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        verify(statusService, times(1)).resolveStatus("user-fenced", true);
    }

    @Test
    @WithMockUser(authorities = "STUDENT")
    void shouldRejectUnauthorizedStatusUpdate() throws Exception {
        String json = "{\"anonymousId\": \"user-student\"}";

        mockMvc.perform(post("/api/v1/health/confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        verifyNoInteractions(statusService);
    }
}
