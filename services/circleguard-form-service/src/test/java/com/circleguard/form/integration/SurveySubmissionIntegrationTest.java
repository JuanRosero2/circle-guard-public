package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.HealthSurveyRepository;
import com.circleguard.form.service.QuestionnaireService;
import com.circleguard.form.service.SymptomMapper;
import com.circleguard.form.service.HealthSurveyService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test: Form service survey submission → Kafka event emission.
 * Validates that submitting a survey with symptoms emits the correct Kafka event.
 */
class SurveySubmissionIntegrationTest {

    @Test
    void shouldEmitSurveySubmittedEventWithSymptomsFlag() {
        // Setup mocks
        HealthSurveyRepository repository = mock(HealthSurveyRepository.class);
        QuestionnaireService questionnaireService = mock(QuestionnaireService.class);
        SymptomMapper symptomMapper = new SymptomMapper(); // use real implementation
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        HealthSurveyService service = new HealthSurveyService(repository, questionnaireService, symptomMapper, kafkaTemplate);

        UUID questionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(Question.builder()
                        .id(questionId)
                        .text("¿Tiene fiebre?")
                        .type(QuestionType.YES_NO)
                        .build()))
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(userId)
                .responses(Map.of(questionId.toString(), "YES"))
                .build();

        // Execute
        HealthSurvey result = service.submitSurvey(survey);

        // Assert
        assertNotNull(result);
        assertTrue(result.getHasFever(), "Survey with YES to fever should mark hasFever=true");

        // Verify Kafka integration: event emitted with hasSymptoms=true
        verify(kafkaTemplate).send(eq("survey.submitted"), eq(userId.toString()), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) payload;
            return (Boolean) map.get("hasSymptoms");
        }));
    }

    @Test
    void shouldEmitCertificateValidatedEventOnSurveyApproval() {
        HealthSurveyRepository repository = mock(HealthSurveyRepository.class);
        QuestionnaireService questionnaireService = mock(QuestionnaireService.class);
        SymptomMapper symptomMapper = mock(SymptomMapper.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        HealthSurveyService service = new HealthSurveyService(repository, questionnaireService, symptomMapper, kafkaTemplate);

        UUID surveyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(userId)
                .attachmentPath("/uploads/cert.pdf")
                .build();

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.validateSurvey(surveyId, com.circleguard.form.model.ValidationStatus.APPROVED, adminId);

        verify(kafkaTemplate).send(eq("certificate.validated"), eq(userId.toString()), any());
    }
}
