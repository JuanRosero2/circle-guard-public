package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthSurveyServiceTest {

    @Mock
    private HealthSurveyRepository repository;
    
    @Mock
    private QuestionnaireService questionnaireService;
    
    @Mock
    private SymptomMapper symptomMapper;
    
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @InjectMocks
    private HealthSurveyService healthSurveyService;
    
    private HealthSurvey testSurvey;
    private Questionnaire testQuestionnaire;
    private UUID surveyId;

    @BeforeEach
    void setUp() {
        surveyId = UUID.randomUUID();
        testSurvey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(UUID.randomUUID())
                .hasFever(null)
                .hasCough(null)
                .attachmentPath(null)
                .validationStatus(ValidationStatus.PENDING)
                .build();
                
        testQuestionnaire = Questionnaire.builder()
                .id(UUID.randomUUID())
                .title("Health Questionnaire")
                .isActive(true)
                .build();
    }

    @Test
    void submitSurvey_WithActiveQuestionnaireAndNoSymptoms_SetsHasFeverAndHasCoughToFalse() {
        // Arrange
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(testQuestionnaire));
        when(symptomMapper.hasSymptoms(testSurvey, testQuestionnaire)).thenReturn(false);
        when(repository.save(any(HealthSurvey.class))).thenReturn(testSurvey);

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(testSurvey);

        // Assert
        assertNotNull(result);
        assertFalse(result.getHasFever());
        assertFalse(result.getHasCough());
        
        verify(questionnaireService).getActiveQuestionnaire();
        verify(symptomMapper).hasSymptoms(testSurvey, testQuestionnaire);
        verify(repository).save(any(HealthSurvey.class));
        // Kafka template verification omitted for simplicity
    }

    @Test
    void submitSurvey_WithActiveQuestionnaireAndSymptoms_SetsHasFeverAndHasCoughToTrue() {
        // Arrange
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(testQuestionnaire));
        when(symptomMapper.hasSymptoms(testSurvey, testQuestionnaire)).thenReturn(true);
        when(repository.save(any(HealthSurvey.class))).thenReturn(testSurvey);

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(testSurvey);

        // Assert
        assertNotNull(result);
        assertTrue(result.getHasFever());
        assertTrue(result.getHasCough());
        
        verify(questionnaireService).getActiveQuestionnaire();
        verify(symptomMapper).hasSymptoms(testSurvey, testQuestionnaire);
        verify(repository).save(any(HealthSurvey.class));
        // Kafka template verification omitted for simplicity
    }

    @Test
    void submitSurvey_WithNoActiveQuestionnaire_SetsHasFeverAndHasCoughToFalse() {
        // Arrange
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(any(HealthSurvey.class))).thenReturn(testSurvey);

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(testSurvey);

        // Assert
        assertNotNull(result);
        assertFalse(result.getHasFever());
        assertFalse(result.getHasCough());
        
        verify(questionnaireService).getActiveQuestionnaire();
        verify(symptomMapper, never()).hasSymptoms(any(), any());
        verify(repository).save(any(HealthSurvey.class));
        // Kafka template verification omitted for simplicity
    }

    @Test
    void submitSurvey_WithAttachmentPath_SetsValidationStatusToPending() {
        // Arrange
        testSurvey.setAttachmentPath("/path/to/attachment.pdf");
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(testQuestionnaire));
        when(symptomMapper.hasSymptoms(testSurvey, testQuestionnaire)).thenReturn(false);
        when(repository.save(any(HealthSurvey.class))).thenReturn(testSurvey);

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(testSurvey);

        // Assert
        assertNotNull(result);
        assertEquals(ValidationStatus.PENDING, result.getValidationStatus());
        assertNotNull(result.getAttachmentPath());
        
        verify(repository).save(any(HealthSurvey.class));
        // Kafka template verification omitted for simplicity
    }

    @Test
    void submitSurvey_WithExistingHasFeverAndHasCough_DoesNotOverrideValues() {
        // Arrange
        testSurvey.setHasFever(true);
        testSurvey.setHasCough(false);
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(testQuestionnaire));
        when(symptomMapper.hasSymptoms(testSurvey, testQuestionnaire)).thenReturn(false);
        when(repository.save(any(HealthSurvey.class))).thenReturn(testSurvey);

        // Act
        HealthSurvey result = healthSurveyService.submitSurvey(testSurvey);

        // Assert
        assertNotNull(result);
        assertTrue(result.getHasFever());
        assertFalse(result.getHasCough());
        
        verify(questionnaireService).getActiveQuestionnaire();
        verify(symptomMapper).hasSymptoms(testSurvey, testQuestionnaire);
        verify(repository).save(any(HealthSurvey.class));
        // Kafka template verification omitted for simplicity
    }
}
