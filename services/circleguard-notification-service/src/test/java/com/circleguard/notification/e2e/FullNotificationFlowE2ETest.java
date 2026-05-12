package com.circleguard.notification.e2e;

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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * E2E Test: Full notification flow when a student is exposed.
 * Simulates the chain: promotion-service emits Kafka event → notification-service receives it
 * → dispatches notification + syncs LMS attendance.
 */
@ExtendWith(MockitoExtension.class)
class FullNotificationFlowE2ETest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private LmsService lmsService;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExposureNotificationListener listener;

    @Test
    void shouldDispatchNotificationAndSyncLmsWhenStudentIsConfirmed() {
        // Simulate Kafka message: student confirmed COVID positive
        String kafkaEventJson = "{\"anonymousId\": \"student-e2e-001\", \"status\": \"CONFIRMED\"}";

        listener.handleStatusChange(kafkaEventJson);

        // Both notification dispatch and LMS sync should be triggered
        verify(notificationDispatcher).dispatch(eq("student-e2e-001"), eq("CONFIRMED"));
        verify(lmsService).syncRemoteAttendance("student-e2e-001", "CONFIRMED");
    }

    @Test
    void shouldDispatchNotificationWhenStudentIsSuspect() {
        String kafkaEventJson = "{\"anonymousId\": \"student-e2e-002\", \"status\": \"SUSPECT\"}";

        listener.handleStatusChange(kafkaEventJson);

        verify(notificationDispatcher).dispatch(eq("student-e2e-002"), eq("SUSPECT"));
        verify(lmsService).syncRemoteAttendance("student-e2e-002", "SUSPECT");
    }

    @Test
    void shouldSkipNotificationForNormalActiveStatus() {
        // ACTIVE status = normal, no notification needed
        String kafkaEventJson = "{\"anonymousId\": \"student-e2e-normal\", \"status\": \"ACTIVE\"}";

        listener.handleStatusChange(kafkaEventJson);

        verifyNoInteractions(notificationDispatcher);
        verifyNoInteractions(lmsService);
    }
}
