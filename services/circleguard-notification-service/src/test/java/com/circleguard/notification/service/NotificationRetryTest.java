package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class NotificationRetryTest {

    @TestConfiguration
    static class Config {
        @Bean
        public WebClient.Builder webClientBuilder() {
            WebClient.Builder builder = mock(WebClient.Builder.class);
            when(builder.baseUrl(anyString())).thenReturn(builder);
            when(builder.build()).thenReturn(mock(WebClient.class));
            return builder;
        }
    }

    @Autowired
    private EmailService emailService;

    @MockBean
    private JavaMailSender mailSender;


    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private Configuration freemarkerConfig;

    @MockBean
    private ObjectMapper objectMapper;

    @MockBean
    private TemplateService templateService;

    @MockBean
    private LmsService lmsService;

    @MockBean
    private NotificationDispatcher notificationDispatcher;

    @MockBean
    private SmsService smsService;

    @MockBean
    private PushService pushService;

    @Test
    void testEmailRetryLogic() throws Exception {
        // Force failure for all attempts
        doThrow(new RuntimeException("Mail server down"))
            .when(mailSender).send(any(SimpleMailMessage.class));

        CompletableFuture<Void> future = emailService.sendAsync("user-1", "test message");
        
        // Wait for retries to complete (3 attempts with 2s backoff)
        try {
            future.join();
        } catch (Exception e) {
            // Expected
        }

        // Verify mailSender.send was called exactly 3 times
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
        
        // Verify audit logs were emitted
        verify(kafkaTemplate, atLeast(3)).send(eq("notification.audit"), anyString(), anyMap());
    }
}
