package com.circleguard.promotion.e2e;

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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E Test: Full health status update flow.
 * Simulates a health center confirming a positive COVID case and then later resolving it.
 * Tests the complete authorization + business logic cycle.
 */
@WebMvcTest(HealthStatusController.class)
@Import(SecurityConfig.class)
@org.springframework.test.context.TestPropertySource(properties = "jwt.secret=my-super-secret-dev-key-with-more-than-sixty-four-characters-for-safety-1234567890")
class HealthStatusUpdateFlowE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthStatusService statusService;

    @Test
    @WithMockUser(authorities = "HEALTH_CENTER")
    void healthCenterShouldConfirmPositiveCaseSuccessfully() throws Exception {
        String userId = "user-positive-001";
        doNothing().when(statusService).updateStatus(userId, "CONFIRMED");

        mockMvc.perform(post("/api/v1/health/confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\": \"" + userId + "\"}"))
                .andExpect(status().isOk());

        verify(statusService).updateStatus(userId, "CONFIRMED");
    }

    @Test
    @WithMockUser(authorities = "HEALTH_CENTER")
    void healthCenterShouldResolveUserStatusAfterQuarantine() throws Exception {
        String userId = "user-recovered-001";
        doNothing().when(statusService).resolveStatus(userId, false);

        mockMvc.perform(post("/api/v1/health/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\": \"" + userId + "\"}"))
                .andExpect(status().isOk());

        verify(statusService).resolveStatus(userId, false);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminShouldBeAbleToOverrideResolution() throws Exception {
        String userId = "user-fenced-001";
        doNothing().when(statusService).resolveStatus(userId, true);

        mockMvc.perform(post("/api/v1/health/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\": \"" + userId + "\", \"override\": true}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "STUDENT")
    void studentShouldNotBeAbleToConfirmPositiveCase() throws Exception {
        mockMvc.perform(post("/api/v1/health/confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\": \"user-student\"}"))
                .andExpect(status().isForbidden());
    }
}
