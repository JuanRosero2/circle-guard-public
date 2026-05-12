package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
class ExposureNotificationListenerTest {

    @Autowired
    private ExposureNotificationListener listener;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private Configuration freemarkerConfig;

    @MockBean
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationDispatcher dispatcher;

    @MockBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @MockBean
    private WebClient.Builder webClientBuilder;

    @MockBean
    private LmsService lmsService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private SmsService smsService;

    @MockBean
    private PushService pushService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private TemplateService templateService;

    @Test
    void shouldHandleStatusChangeEventWithoutError() {
        String mockEvent = "{\"userId\": \"user-123\", \"newStatus\": \"EXPOSED\"}";
        assertDoesNotThrow(() -> listener.handleStatusChange(mockEvent));
    }
}
