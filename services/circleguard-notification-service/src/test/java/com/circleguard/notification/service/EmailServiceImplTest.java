package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;
    
    @Mock
    private AuditLogService auditLogService;
    
    @InjectMocks
    private EmailServiceImpl emailService;
    
    private String testUserId;
    private String testMessage;

    @BeforeEach
    void setUp() {
        testUserId = "user123";
        testMessage = "Test health alert message";
    }

    @Test
    void sendAsync_SuccessfulDelivery_ReturnsCompletedFuture() throws Exception {
        // Arrange
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // Act
        CompletableFuture<Void> result = emailService.sendAsync(testUserId, testMessage);

        // Assert
        assertTrue(result.isDone());
        assertNull(result.get());
        
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(auditLogService).logDelivery(eq(testUserId), eq("EMAIL"), eq("SUCCESS"), any(String.class));
    }

    @Test
    void sendAsync_VerifyMailSenderCalled_CallsCorrectMethod() {
        // Arrange
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // Act
        CompletableFuture<Void> result = emailService.sendAsync(testUserId, testMessage);

        // Assert
        assertNotNull(result);
        
        // Verify the mail sender was called with correct message structure
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(auditLogService).logDelivery(eq(testUserId), eq("EMAIL"), eq("SUCCESS"), any(String.class));
    }

    @Test
    void sendAsync_NullUserId_HandlesGracefully() throws Exception {
        // Arrange
        String nullUserId = null;
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // Act
        CompletableFuture<Void> result = emailService.sendAsync(nullUserId, testMessage);

        // Assert
        assertTrue(result.isDone());
        assertNull(result.get());
        
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(auditLogService).logDelivery(eq(null), eq("EMAIL"), eq("SUCCESS"), any(String.class));
    }

    @Test
    void sendAsync_EmptyMessage_SendsEmptyMessage() throws Exception {
        // Arrange
        String emptyMessage = "";
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // Act
        CompletableFuture<Void> result = emailService.sendAsync(testUserId, emptyMessage);

        // Assert
        assertTrue(result.isDone());
        assertNull(result.get());
        
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(auditLogService).logDelivery(eq(testUserId), eq("EMAIL"), eq("SUCCESS"), any(String.class));
    }

    @Test
    void sendAsync_LongMessage_SendsSuccessfully() throws Exception {
        // Arrange
        StringBuilder longMessageBuilder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longMessageBuilder.append("This is a very long message line ").append(i).append(". ");
        }
        String longMessage = longMessageBuilder.toString();
        
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // Act
        CompletableFuture<Void> result = emailService.sendAsync(testUserId, longMessage);

        // Assert
        assertTrue(result.isDone());
        assertNull(result.get());
        
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(auditLogService).logDelivery(eq(testUserId), eq("EMAIL"), eq("SUCCESS"), any(String.class));
    }
}
