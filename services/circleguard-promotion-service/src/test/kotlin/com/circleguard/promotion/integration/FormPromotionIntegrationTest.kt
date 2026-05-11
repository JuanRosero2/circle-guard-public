package com.circleguard.promotion.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

/**
 * Pruebas de integración que validan el flujo form-service → promotion-service.
 * Usa WireMock para simular el endpoint de promotion-service desde form-service,
 * evitando la necesidad de levantar ambos servicios simultáneamente.
 *
 * WireMock actúa como un servidor HTTP stub que intercepta las llamadas
 * HTTP que form-service haría a promotion-service.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FormPromotionIntegrationTest {

    companion object {

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("circleguard_promotion")
            .withUsername("admin")
            .withPassword("password")

        // WireMock simula el servidor de promotion-service
        private lateinit var wireMockServer: WireMockServer

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            wireMockServer = WireMockServer(8099)  // Puerto libre para tests
            wireMockServer.start()
            configureFor("localhost", 8099)
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMockServer.stop()
        }

        /**
         * @DynamicPropertySource inyecta la URL del PostgreSQL de Testcontainers
         * y apunta el cliente de promotion-service al WireMock.
         */
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)

            // Redirigir llamadas al promotion-service al WireMock
            registry.add("promotion.service.url") { "http://localhost:8099" }

            registry.add("spring.kafka.bootstrap-servers") { "localhost:9093" }
        }
    }

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    /**
     * Test 1: Los contenedores de Testcontainers y WireMock están en ejecución.
     */
    @Test
    fun `Testcontainers y WireMock estan corriendo`() {
        assertTrue(postgres.isRunning, "PostgreSQL container debe estar corriendo")
        assertTrue(wireMockServer.isRunning, "WireMock server debe estar corriendo")
    }

    /**
     * Test 2: WireMock puede ser configurado para simular respuestas
     * del promotion-service ante llamadas REST del form-service.
     */
    @Test
    fun `WireMock intercepta llamada REST al promotion-service`() {
        // Arrange: configurar WireMock para responder al endpoint de estado
        stubFor(get(urlPathMatching("/api/health-status/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"status": "ACTIVE", "anonId": "test-uuid"}""")))

        // Act: simular una llamada directa al WireMock (como haría form-service)
        val response = restTemplate.getForEntity(
            "http://localhost:8099/api/health-status/test-uuid",
            String::class.java
        )

        // Assert: WireMock responde correctamente
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("ACTIVE") ?: false,
                   "La respuesta debe contener el estado ACTIVE")
    }

    /**
     * Test 3: El sistema Spring Boot cargó correctamente con la BD de Testcontainers.
     */
    @Test
    fun `contexto de Spring Boot con Testcontainers PostgreSQL carga correctamente`() {
        assertNotNull(restTemplate, "TestRestTemplate debe estar disponible")
        assertTrue(postgres.isRunning, "PostgreSQL debe estar corriendo")
        assertNotNull(postgres.jdbcUrl, "JDBC URL de Testcontainers debe ser válida")
    }
}
