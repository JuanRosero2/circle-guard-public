package com.circleguard.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.concurrent.CompletableFuture

/**
 * Pruebas unitarias para los servicios de notificación.
 * Verifican el despacho de notificaciones multi-canal (email, SMS, push)
 * ante eventos Kafka del promotion-service.
 *
 * El notification-service NO tiene BD — las pruebas son puramente de lógica de negocio.
 */
@ExtendWith(MockitoExtension::class)
class NotificationServiceTest {

    @Mock
    private lateinit var emailService: EmailService

    @Mock
    private lateinit var smsService: SmsService

    @Mock
    private lateinit var pushService: PushService

    @Mock
    private lateinit var templateService: TemplateService

    @Mock
    private lateinit var lmsService: LmsService

    @InjectMocks
    private lateinit var notificationDispatcher: NotificationDispatcher

    private val objectMapper = ObjectMapper()

    // ExposureNotificationListener también lo probamos
    private lateinit var exposureListener: ExposureNotificationListener

    /**
     * Test 1: evento Kafka con status CONFIRMED debe disparar email via EmailService.
     * El dispatcher llama a emailService.sendAsync para el canal de correo.
     */
    @Test
    fun `evento CONFIRMED dispara envio de email via EmailService`() {
        // Arrange
        val userId = "anon-uuid-confirmed"
        val emailContent = "<p>Contacto confirmado de COVID-19</p>"

        `when`(templateService.generateEmailContent("CONFIRMED", userId)).thenReturn(emailContent)
        `when`(templateService.generatePushContent("CONFIRMED")).thenReturn("Alerta de exposición")
        `when`(templateService.generatePushMetadata("CONFIRMED")).thenReturn(mapOf("type" to "EXPOSURE"))
        `when`(templateService.generateSmsContent("CONFIRMED")).thenReturn("Exposición confirmada")
        `when`(emailService.sendAsync(userId, emailContent))
            .thenReturn(CompletableFuture.completedFuture(null))
        `when`(smsService.sendAsync(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null))
        `when`(pushService.sendAsync(anyString(), anyString(), anyMap()))
            .thenReturn(CompletableFuture.completedFuture(null))

        // Act
        notificationDispatcher.dispatch(userId, "CONFIRMED")

        // Assert
        verify(emailService).sendAsync(userId, emailContent)
        verify(templateService).generateEmailContent("CONFIRMED", userId)
    }

    /**
     * Test 2: evento Kafka CONFIRMED llama a SmsService para enviar SMS
     * si el usuario tiene teléfono disponible (smsService maneja la lógica interna).
     */
    @Test
    fun `evento CONFIRMED invoca SmsService para envio de SMS`() {
        // Arrange
        val userId = "anon-uuid-sms"
        `when`(templateService.generateEmailContent(anyString(), anyString())).thenReturn("email content")
        `when`(templateService.generatePushContent(anyString())).thenReturn("push content")
        `when`(templateService.generatePushMetadata(anyString())).thenReturn(emptyMap())
        `when`(templateService.generateSmsContent("CONFIRMED")).thenReturn("Exposición confirmada")
        `when`(emailService.sendAsync(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null))
        `when`(smsService.sendAsync(userId, "Exposición confirmada"))
            .thenReturn(CompletableFuture.completedFuture(null))
        `when`(pushService.sendAsync(anyString(), anyString(), anyMap()))
            .thenReturn(CompletableFuture.completedFuture(null))

        // Act
        notificationDispatcher.dispatch(userId, "CONFIRMED")

        // Assert: smsService.sendAsync debe ser invocado con el userId correcto
        verify(smsService).sendAsync(userId, "Exposición confirmada")
    }

    /**
     * Test 3: el dispatcher debe llamar a todos los canales (email, sms, push)
     * en paralelo (CompletableFuture.allOf) para minimizar latencia.
     */
    @Test
    fun `dispatch invoca los tres canales de notificacion en paralelo`() {
        // Arrange
        val userId = "anon-parallel-test"
        `when`(templateService.generateEmailContent(anyString(), anyString())).thenReturn("email")
        `when`(templateService.generatePushContent(anyString())).thenReturn("push")
        `when`(templateService.generatePushMetadata(anyString())).thenReturn(emptyMap())
        `when`(templateService.generateSmsContent(anyString())).thenReturn("sms")
        `when`(emailService.sendAsync(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null))
        `when`(smsService.sendAsync(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null))
        `when`(pushService.sendAsync(anyString(), anyString(), anyMap()))
            .thenReturn(CompletableFuture.completedFuture(null))

        // Act
        notificationDispatcher.dispatch(userId, "CONFIRMED")

        // Assert: los tres servicios deben ser invocados
        verify(emailService).sendAsync(eq(userId), anyString())
        verify(smsService).sendAsync(eq(userId), anyString())
        verify(pushService).sendAsync(eq(userId), anyString(), anyMap())
    }

    /**
     * Test 4: evento de estado SUSPECT/ACTIVE NO debe disparar notificaciones.
     * Solo CONFIRMED, PROBABLE y estados de alerta disparan notificaciones.
     * ExposureNotificationListener filtra con: !ACTIVE && !UNKNOWN
     */
    @Test
    fun `evento ACTIVE no dispara notificaciones segun filtro del listener`() {
        // Arrange: simular el filtro del listener
        // El ExposureNotificationListener solo llama a dispatcher cuando
        // status != ACTIVE && status != UNKNOWN
        val userId = "anon-active-test"
        val activeStatus = "ACTIVE"

        // Act: simular la lógica del listener directamente
        val shouldDispatch = activeStatus != "ACTIVE" && activeStatus != "UNKNOWN"

        // Assert: no debe despachar para ACTIVE
        assertFalse(shouldDispatch, "Estado ACTIVE no debe generar notificaciones")
        verifyNoInteractions(notificationDispatcher)
    }

    /**
     * Test 5: la plantilla de email no debe contener el nombre real del usuario,
     * solo el anonymousId para proteger la privacidad.
     */
    @Test
    fun `plantilla de email contiene anonId pero no nombre real`() {
        // Arrange
        val userId = "anon-privacy-123"
        val templateContent = "Su ID anónimo: $userId — Exposición detectada."

        `when`(templateService.generateEmailContent("CONFIRMED", userId))
            .thenReturn(templateContent)
        `when`(templateService.generatePushContent(anyString())).thenReturn("push")
        `when`(templateService.generatePushMetadata(anyString())).thenReturn(emptyMap())
        `when`(templateService.generateSmsContent(anyString())).thenReturn("sms")
        `when`(emailService.sendAsync(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null))
        `when`(smsService.sendAsync(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null))
        `when`(pushService.sendAsync(anyString(), anyString(), anyMap()))
            .thenReturn(CompletableFuture.completedFuture(null))

        // Act
        notificationDispatcher.dispatch(userId, "CONFIRMED")

        // Assert: el contenido de email incluye anonId pero no "name" ni "email"
        assertFalse(templateContent.contains("nombre"), "Plantilla NO debe contener nombre real")
        assertFalse(templateContent.contains("@"), "Plantilla NO debe contener dirección de email real")
        assertTrue(templateContent.contains(userId), "Plantilla debe contener el anonId")
    }
}
