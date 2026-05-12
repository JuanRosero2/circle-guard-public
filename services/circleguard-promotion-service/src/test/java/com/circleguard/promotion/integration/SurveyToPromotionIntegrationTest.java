package com.circleguard.promotion.integration;

import com.circleguard.promotion.listener.SurveyListener;
import com.circleguard.promotion.service.HealthStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Integration test for the event-driven flow between Form Service and Promotion Service.
 * Validates that a 'survey.submitted' event with symptoms triggers a status update to SUSPECT.
 */
@SpringBootTest
@ActiveProfiles("test")
class SurveyToPromotionIntegrationTest {

    @Autowired
    private SurveyListener surveyListener;

    @MockBean
    private HealthStatusService healthStatusService;

    @Test
    void shouldPromoteUserToSuspectWhenSymptomaticSurveyReceived() {
        // Arrange
        String anonymousId = "user-with-symptoms-123";
        Map<String, Object> event = new HashMap<>();
        event.put("anonymousId", anonymousId);
        event.put("hasSymptoms", true);

        // Act: Manually invoke the listener method to simulate Kafka message reception
        // (In a full E2E test this would go through actual Kafka)
        surveyListener.onSurveySubmitted(event);

        // Assert
        verify(healthStatusService, timeout(1000)).updateStatus(eq(anonymousId), eq("SUSPECT"));
    }

    @Test
    void shouldNotPromoteUserWhenHealthySurveyReceived() {
        // Arrange
        String anonymousId = "healthy-user-456";
        Map<String, Object> event = new HashMap<>();
        event.put("anonymousId", anonymousId);
        event.put("hasSymptoms", false);

        // Act
        surveyListener.onSurveySubmitted(event);

        // Assert
        verify(healthStatusService, Mockito.never()).updateStatus(Mockito.anyString(), Mockito.anyString());
    }
}
