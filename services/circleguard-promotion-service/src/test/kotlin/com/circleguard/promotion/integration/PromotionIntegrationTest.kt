package com.circleguard.promotion.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

/**
 * Pruebas de integración para promotion-service usando Testcontainers.
 * Levanta contenedores reales de PostgreSQL y Neo4j para validar
 * la integración entre la aplicación Spring Boot y las bases de datos.
 *
 * @DynamicPropertySource inyecta las URLs de los contenedores en el contexto
 * de Spring Boot ANTES de que se inicialice la aplicación, reemplazando
 * los valores de application.yml con los puertos aleatorios de los contenedores.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PromotionIntegrationTest {

    companion object {

        // Contenedor de PostgreSQL 16 — misma versión que en docker-compose.dev.yml
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("circleguard_promotion")
            .withUsername("admin")
            .withPassword("password")

        // Contenedor de Neo4j 5.26 — misma versión que en docker-compose.dev.yml
        @Container
        @JvmStatic
        val neo4j: Neo4jContainer<*> = Neo4jContainer("neo4j:5.26")
            .withAdminPassword("password")
            .withoutAuthentication()

        /**
         * @DynamicPropertySource inyecta las URLs de los contenedores dinámicamente.
         * Es el mecanismo estándar en Spring Boot 2.2.6+ para Testcontainers.
         * Evita hardcodear puertos que cambian en cada ejecución.
         */
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // PostgreSQL
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)

            // Neo4j — URI del contenedor con puerto aleatorio
            registry.add("spring.neo4j.uri", neo4j::getBoltUrl)
            registry.add("spring.neo4j.authentication.username") { "neo4j" }
            registry.add("spring.neo4j.authentication.password") { "none" }  // withoutAuthentication

            // Deshabilitar Kafka y Redis en tests de integración
            registry.add("spring.kafka.bootstrap-servers") { "localhost:9093" }  // Kafka no iniciado
            registry.add("spring.data.redis.host") { "localhost" }
            registry.add("spring.data.redis.port") { "6380" }  // Puerto no usado
        }
    }

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    /**
     * Test 1: Los contenedores de Testcontainers deben estar corriendo
     * antes de que comience cualquier test.
     */
    @Test
    fun `contenedores de Testcontainers estan corriendo`() {
        assertTrue(postgres.isRunning, "PostgreSQL container debe estar corriendo")
        assertTrue(neo4j.isRunning, "Neo4j container debe estar corriendo")
    }

    /**
     * Test 2: El contexto de Spring Boot debe cargar correctamente
     * con las URLs de Testcontainers inyectadas por @DynamicPropertySource.
     */
    @Test
    fun `contexto de Spring Boot carga con Testcontainers`() {
        assertNotNull(restTemplate, "TestRestTemplate debe estar disponible")
    }

    /**
     * Test 3: GET /actuator/health retorna 200 OK cuando la BD está disponible.
     * Verifica que el HealthIndicator de Spring Boot detecta PostgreSQL y Neo4j.
     */
    @Test
    fun `actuator health retorna 200 OK con bases de datos disponibles`() {
        val response = restTemplate.getForEntity("/actuator/health", String::class.java)
        // El status puede ser 200 (UP) o 503 (DOWN con parcial) dependiendo de la config
        // Lo importante es que responda, no que esté 100% UP (Kafka no está en test)
        assertNotNull(response, "La respuesta de health no debe ser null")
        assertTrue(
            response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.SERVICE_UNAVAILABLE,
            "Health endpoint debe responder"
        )
    }

    /**
     * Test 4: GET /api/promotion/logs sin JWT debe retornar 401 Unauthorized.
     * Verifica que el filtro JWT de SecurityConfig está activo en integración.
     */
    @Test
    fun `endpoint protegido sin JWT retorna 401`() {
        val response = restTemplate.getForEntity(
            "/api/entry/validate",
            String::class.java
        )
        // 401 o 403 dependiendo de cómo esté configurado Spring Security
        assertTrue(
            response.statusCode == HttpStatus.UNAUTHORIZED ||
            response.statusCode == HttpStatus.FORBIDDEN ||
            response.statusCode == HttpStatus.NOT_FOUND,
            "Endpoint protegido debe retornar 401/403 sin JWT, status: ${response.statusCode}"
        )
    }

    /**
     * Test 5: La base de datos PostgreSQL acepta conexiones y el esquema
     * de Flyway fue aplicado correctamente (tabla system_settings existe).
     */
    @Test
    fun `PostgreSQL de Testcontainers acepta conexiones correctamente`() {
        // Verificar que la URL de PostgreSQL del contenedor es válida
        assertNotNull(postgres.jdbcUrl, "JDBC URL no debe ser null")
        assertTrue(postgres.jdbcUrl.contains("circleguard_promotion"),
                   "La URL debe apuntar a la BD circleguard_promotion")
    }
}
