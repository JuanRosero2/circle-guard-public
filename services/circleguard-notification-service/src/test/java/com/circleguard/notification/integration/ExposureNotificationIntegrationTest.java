package com.circleguard.notification.integration;

import com.circleguard.notification.service.ExposureNotificationListener;
import com.circleguard.notification.service.LmsService;
import com.circleguard.notification.service.NotificationDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: Notification listener → dispatcher chain.
 * Validates that a status-change event triggers a notification dispatch via the Kafka listener method.
 */
@ExtendWith(MockitoExtension.class)
class ExposureNotificationIntegrationTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private LmsService lmsService;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExposureNotificationListener listener;

    @Test
    void shouldDispatchNotificationWhenConfirmedStatusEventReceived() {
        String eventJson = "{\"anonymousId\": \"user-abc-123\", \"status\": \"CONFIRMED\"}";

        listener.handleStatusChange(eventJson);

        verify(notificationDispatcher, times(1)).dispatch(eq("user-abc-123"), eq("CONFIRMED"));
        verify(lmsService, times(1)).syncRemoteAttendance("user-abc-123", "CONFIRMED");
    }

    @Test
    void shouldNotDispatchForActiveStatus() {
        // ACTIVE status should be skipped (normal state)
        String eventJson = "{\"anonymousId\": \"user-active\", \"status\": \"ACTIVE\"}";

        listener.handleStatusChange(eventJson);

        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void shouldHandleMalformedJsonGracefully() {
        String badEvent = "not-valid-json";

        assertDoesNotThrow(() -> listener.handleStatusChange(badEvent),
                "Malformed JSON should not throw uncaught exception");

        verifyNoInteractions(notificationDispatcher);
    }
}
