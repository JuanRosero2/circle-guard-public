package com.circleguard.form.e2e;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.form.controller.HealthSurveyController;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E Test: Full survey submission flow.
 * Simulates a student submitting a health survey with symptoms.
 * Tests the complete HTTP cycle from submission to saved survey response.
 */
@WebMvcTest(HealthSurveyController.class)
class SurveySubmissionFlowE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthSurveyService surveyService;

    @Test
    void studentShouldSubmitSurveyWithSymptomsAndReceiveSurveyId() throws Exception {
        UUID surveyId = UUID.randomUUID();
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        HealthSurvey savedSurvey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(userId)
                .hasFever(true)
                .build();

        Mockito.when(surveyService.submitSurvey(Mockito.any())).thenReturn(savedSurvey);

        String surveyJson = """
                {
                    "anonymousId": "22222222-2222-2222-2222-222222222222",
                    "hasFever": true,
                    "hasCough": false
                }
                """;

        mockMvc.perform(post("/api/v1/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(surveyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.anonymousId").value(userId.toString()))
                .andExpect(jsonPath("$.hasFever").value(true));
    }

    @Test
    void studentShouldSubmitSurveyWithoutSymptomsSuccessfully() throws Exception {
        UUID surveyId = UUID.randomUUID();
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        HealthSurvey savedSurvey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(userId)
                .hasFever(false)
                .hasCough(false)
                .build();

        Mockito.when(surveyService.submitSurvey(Mockito.any())).thenReturn(savedSurvey);

        String surveyJson = """
                {
                    "anonymousId": "33333333-3333-3333-3333-333333333333",
                    "hasFever": false,
                    "hasCough": false
                }
                """;

        mockMvc.perform(post("/api/v1/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(surveyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasFever").value(false));
    }
}
