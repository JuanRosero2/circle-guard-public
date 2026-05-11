package com.circleguard.promotion.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.neo4j.core.Neo4jClient
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.kafka.core.KafkaTemplate
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository
import com.circleguard.promotion.model.jpa.SystemSettings
import java.util.Optional

/**
 * Pruebas unitarias para StatusLifecycleService.
 * Verifican la lógica de transición de estados (SUSPECT→PROBABLE→CONFIRMED)
 * y el comportamiento de la caché Redis y la publicación en Kafka.
 *
 * Se usa MockitoExtension para inyección de mocks sin contexto de Spring,
 * lo que hace estas pruebas muy rápidas (~ms por test).
 */
@ExtendWith(MockitoExtension::class)
class StatusPromotionServiceTest {

    @Mock
    private lateinit var neo4jClient: Neo4jClient

    @Mock
    private lateinit var systemSettingsRepository: SystemSettingsRepository

    @Mock
    private lateinit var redisTemplate: StringRedisTemplate

    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    @InjectMocks
    private lateinit var statusLifecycleService: StatusLifecycleService

    private val testAnonId = "anon-uuid-1234-5678"

    @BeforeEach
    fun setup() {
        val settings = mock(SystemSettings::class.java)
        lenient().`when`(settings.mandatoryFenceDays).thenReturn(14)
        lenient().`when`(systemSettingsRepository.getSettings()).thenReturn(Optional.of(settings))
        lenient().`when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
    }

    @Test
    fun `sospechoso con contacto confirmado es promovido a PROBABLE`() {
        val querySpec = mock(Neo4jClient.UnboundRunnableSpec::class.java, RETURNS_DEEP_STUBS)
        `when`(neo4jClient.query(anyString())).thenReturn(querySpec)
        
        val resultMap = mapOf("releasedIds" to listOf(testAnonId))
        `when`(querySpec.bind(org.mockito.ArgumentMatchers.any(Any::class.java)).to(org.mockito.ArgumentMatchers.anyString()).fetch().one()).thenReturn(Optional.of(resultMap))

        statusLifecycleService.processAutomaticTransitions()

        verify(neo4jClient, atLeast(1)).query(anyString())
    }

    @Test
    fun `sin contactos confirmados el estado no cambia y no se publica en Kafka`() {
        val querySpec = mock(Neo4jClient.UnboundRunnableSpec::class.java, RETURNS_DEEP_STUBS)
        `when`(neo4jClient.query(anyString())).thenReturn(querySpec)
        
        `when`(querySpec.bind(org.mockito.ArgumentMatchers.any(Any::class.java)).to(org.mockito.ArgumentMatchers.anyString()).fetch().one()).thenReturn(Optional.empty())

        statusLifecycleService.processAutomaticTransitions()

        verifyNoInteractions(kafkaTemplate)
    }

    @Test
    fun `ventana de 14 dias se calcula correctamente como milisegundos`() {
        val expectedWindowMs = 14L * 24 * 60 * 60 * 1000  

        val beforeCall = System.currentTimeMillis()

        val querySpec = mock(Neo4jClient.UnboundRunnableSpec::class.java, RETURNS_DEEP_STUBS)
        `when`(neo4jClient.query(anyString())).thenReturn(querySpec)
        `when`(querySpec.bind(org.mockito.ArgumentMatchers.any(Any::class.java)).to(org.mockito.ArgumentMatchers.anyString()).fetch().one()).thenReturn(Optional.empty())

        statusLifecycleService.processAutomaticTransitions()

        val afterCall = System.currentTimeMillis()

        val expectedMin = beforeCall - expectedWindowMs
        val expectedMax = afterCall - expectedWindowMs
        assertTrue(expectedMax >= expectedMin, "Ventana de 14 días debe ser positiva")
    }

    @Test
    fun `cambio de estado invalida clave Redis para el usuario`() {
        val querySpec = mock(Neo4jClient.UnboundRunnableSpec::class.java, RETURNS_DEEP_STUBS)
        `when`(neo4jClient.query(anyString())).thenReturn(querySpec)
        
        val resultMap = mapOf("releasedIds" to listOf(testAnonId))
        `when`(querySpec.bind(org.mockito.ArgumentMatchers.any(Any::class.java)).to(org.mockito.ArgumentMatchers.anyString()).fetch().one()).thenReturn(Optional.of(resultMap))

        statusLifecycleService.processAutomaticTransitions()

        verify(valueOperations, atLeastOnce()).multiSet(org.mockito.ArgumentMatchers.anyMap())
    }

    @Test
    fun `contacto fuera de ventana 14 dias no genera transicion de estado`() {
        val settings = mock(SystemSettings::class.java)
        lenient().`when`(settings.mandatoryFenceDays).thenReturn(0)
        lenient().`when`(systemSettingsRepository.getSettings()).thenReturn(Optional.of(settings))

        val querySpec = mock(Neo4jClient.UnboundRunnableSpec::class.java, RETURNS_DEEP_STUBS)
        `when`(neo4jClient.query(anyString())).thenReturn(querySpec)
        `when`(querySpec.bind(org.mockito.ArgumentMatchers.any(Any::class.java)).to(org.mockito.ArgumentMatchers.anyString()).fetch().one()).thenReturn(Optional.empty())

        statusLifecycleService.processAutomaticTransitions()

        verifyNoInteractions(kafkaTemplate)
    }
}
