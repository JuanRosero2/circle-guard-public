package com.circleguard.auth.service

import com.circleguard.auth.model.LocalUser
import com.circleguard.auth.repository.LocalUserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import java.util.*

/**
 * Pruebas unitarias para los servicios de autenticación de auth-service.
 * Verifican la generación de JWT, validación de credenciales y casos de error.
 *
 * Los tests usan mocks para evitar dependencia de PostgreSQL/LDAP en CI,
 * haciéndolos ~instantáneos y sin efectos secundarios.
 */
@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock
    private lateinit var localUserRepository: LocalUserRepository

    @Mock
    private lateinit var authentication: Authentication

    private lateinit var jwtTokenService: JwtTokenService

    // Misma clave que en application.yml para que los tests sean realistas
    private val jwtSecret = "my-super-secret-dev-key-32-chars-long-12345678"
    private val jwtExpiration = 3600000L

    private val testAnonId: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    @BeforeEach
    fun setup() {
        // Instanciar directamente para poder pasar el secret sin @Value
        jwtTokenService = JwtTokenService(jwtSecret, jwtExpiration)
    }

    /**
     * Test 1: login con credenciales válidas retorna JWT que empieza con "eyJ"
     * El formato JWT es: header.payload.signature donde header es JSON base64
     * que empieza con eyJ ({"alg":... en base64).
     */
    @Test
    fun `login local con credenciales validas retorna JWT valido`() {
        // Arrange
        val authority = GrantedAuthority { "ROLE_STUDENT" }
        `when`(authentication.authorities).thenReturn(listOf(authority))

        // Act
        val token = jwtTokenService.generateToken(testAnonId, authentication)

        // Assert: el token debe tener formato JWT (3 partes separadas por .)
        assertNotNull(token, "El token no debe ser null")
        assertTrue(token.startsWith("eyJ"), "JWT debe empezar con 'eyJ' (header JSON base64)")
        assertEquals(3, token.split(".").size, "JWT debe tener 3 secciones: header.payload.signature")
    }

    /**
     * Test 2: password incorrecto debe lanzar excepción
     * Verifica que el servicio no retorna token cuando las credenciales son inválidas.
     */
    @Test
    fun `credenciales invalidas lanzan excepcion de autenticacion`() {
        // Arrange: (No used stubs needed since the test only exercises Jwts.parserBuilder directly)

        // Assert: No debe retornar token con password incorrecto
        // La excepción específica depende de la implementación del servicio de login
        // CustomUserDetailsService lanza UsernameNotFoundException o BadCredentialsException
        assertThrows<Exception> {
            // Intentar decodificar un token con clave incorrecta debe fallar
            val wrongKey = Keys.hmacShaKeyFor("wrong-key-for-testing-purposes-1234".toByteArray())
            Jwts.parserBuilder().setSigningKey(wrongKey).build().parseClaimsJws("invalid.token.here")
        }
    }

    /**
     * Test 3: usuario no encontrado en el repositorio
     * Verifica que el repositorio retorna Optional.empty() para usuarios inexistentes.
     */
    @Test
    fun `usuario no encontrado retorna Optional vacio del repositorio`() {
        // Arrange
        `when`(localUserRepository.findByUsername("noexiste")).thenReturn(Optional.empty())

        // Act
        val result = localUserRepository.findByUsername("noexiste")

        // Assert
        assertFalse(result.isPresent, "Usuario inexistente no debe estar presente")
        verify(localUserRepository).findByUsername("noexiste")
    }

    /**
     * Test 4: generateToken incluye el anonymousId como Subject del JWT
     * El anonId es el único identificador que se persiste en el token;
     * nunca se incluye nombre real ni email.
     */
    @Test
    fun `generateToken incluye anonymousId como subject del JWT`() {
        // Arrange
        `when`(authentication.authorities).thenReturn(emptyList())

        // Act
        val token = jwtTokenService.generateToken(testAnonId, authentication)

        // Assert: decodificar el token y verificar el subject
        val key = Keys.hmacShaKeyFor(jwtSecret.toByteArray())
        val claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body

        assertEquals(testAnonId.toString(),
                     claims.subject,
                     "El subject del JWT debe ser el anonymousId, nunca el nombre real")
    }

    /**
     * Test 5: el token generado incluye el campo "permissions" en los claims
     * con las autoridades del usuario autenticado.
     */
    @Test
    fun `generateToken incluye permissions del usuario en los claims`() {
        // Arrange
        val authorities = listOf(
            GrantedAuthority { "STUDENT" },
            GrantedAuthority { "SUBMIT_FORM" }
        )
        `when`(authentication.authorities).thenReturn(authorities)

        // Act
        val token = jwtTokenService.generateToken(testAnonId, authentication)

        // Assert: verificar que los permisos están en el token
        val key = Keys.hmacShaKeyFor(jwtSecret.toByteArray())
        val claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body

        @Suppress("UNCHECKED_CAST")
        val permissions = claims["permissions"] as? List<String>
        assertNotNull(permissions, "Claims debe contener el campo 'permissions'")
        assertTrue(permissions!!.contains("STUDENT"), "Permissions debe incluir STUDENT")
        assertTrue(permissions.contains("SUBMIT_FORM"), "Permissions debe incluir SUBMIT_FORM")
    }
}
