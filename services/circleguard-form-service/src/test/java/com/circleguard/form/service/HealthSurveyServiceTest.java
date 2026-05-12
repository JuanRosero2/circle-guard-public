package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HealthSurveyService.
 * Validates survey submission logic, symptom detection, and Kafka event emission.
 */
class HealthSurveyServiceTest {

    private HealthSurveyRepository surveyRepository;
    private QuestionnaireService questionnaireService;
    private SymptomMapper symptomMapper;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private HealthSurveyService surveyService;

    @BeforeEach
    void setUp() {
        surveyRepository = mock(HealthSurveyRepository.class);
        questionnaireService = mock(QuestionnaireService.class);
        symptomMapper = mock(SymptomMapper.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        surveyService = new HealthSurveyService(surveyRepository, questionnaireService, symptomMapper, kafkaTemplate);
    }

    @Test
    void shouldSubmitSurveyAndEmitKafkaEventWhenSymptomsDetected() {
        UUID qId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(userId)
                .responses(Map.of(qId.toString(), "YES"))
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(Question.builder().id(qId).type(QuestionType.YES_NO).text("Fever?").build()))
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(true);
        when(surveyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = surveyService.submitSurvey(survey);

        assertNotNull(result);
        verify(kafkaTemplate).send(eq("survey.submitted"), eq(userId.toString()), any());
    }

    @Test
    void shouldSubmitSurveyWithoutSymptomsAndStillSave() {
        UUID userId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(userId)
                .responses(Map.of())
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(surveyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = surveyService.submitSurvey(survey);

        assertNotNull(result);
        // hasSymptoms = false (no active questionnaire) → hasFever = false
        assertFalse(Boolean.TRUE.equals(result.getHasFever()));
        verify(kafkaTemplate).send(eq("survey.submitted"), any(), any());
    }

    @Test
    void shouldSetPendingValidationStatusWhenAttachmentIsPresent() {
        UUID userId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(userId)
                .responses(Map.of())
                .attachmentPath("/uploads/cert.pdf")
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(surveyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey result = surveyService.submitSurvey(survey);

        assertEquals(ValidationStatus.PENDING, result.getValidationStatus(),
                "Survey with attachment should have PENDING validation status");
    }
}
