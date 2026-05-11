# Taller 2 CI/CD — Informe Técnico: CircleGuard

## 1. Resumen Ejecutivo

Este documento describe la implementación completa del pipeline CI/CD para el proyecto **CircleGuard**, un sistema de monitoreo de salud epidemiológica para campus universitarios. Se implementaron Dockerfiles, manifiestos Kubernetes, Jenkinsfiles declarativos, pruebas unitarias, de integración, E2E y de rendimiento para 6 microservicios.

---

## 2. Arquitectura del Sistema

```
Mobile App (Expo RN)
    │
    ▼
gateway-service (:8087)  ← valida JWT/QR con Redis
    │
    ├──► auth-service (:8180)       ← emite JWT, LDAP/local auth
    ├──► identity-service (:8083)   ← vault criptográfico, anonimización
    ├──► promotion-service (:8088)  ← motor de estados (Neo4j+Kafka+Redis)
    │         │
    │         └──► Kafka ──► notification-service (:8082) ← email/SMS/push
    └──► form-service (:8086)       ← cuestionarios de salud, Kafka producer
```

### Stack Tecnológico
| Componente | Tecnología |
|---|---|
| Lenguaje | Java 21 + Spring Boot 3.2.4 |
| Build | Gradle (Kotlin DSL), monorepo |
| Container Runtime | Docker / Kubernetes |
| Grafos | Neo4j 5.26 (plugin APOC) |
| Mensajería | Apache Kafka 7.6.0 (Confluent) |
| Caché | Redis 7.2 |
| Base de datos | PostgreSQL 16 |
| Directorio | OpenLDAP 1.5.0 |

---

## 3. Tarea 1: Dockerfiles

### Decisiones de diseño

- **Multi-stage build**: Separación clara entre `eclipse-temurin:21-jdk-alpine` (compilación) y `eclipse-temurin:21-jre-alpine` (runtime). El JRE es ~60% más ligero que el JDK.
- **Contexto de build = raíz del repositorio**: El monorepo Gradle requiere que `gradle/`, `gradlew`, `build.gradle.kts` y `settings.gradle.kts` estén disponibles desde el mismo contexto.
- **Usuario no-root**: `addgroup appgroup && adduser appuser` previene escalación de privilegios.
- **HEALTHCHECK con wget**: Alpine incluye wget por defecto; curl no está disponible sin instalación adicional.
- **JVM flags**: `-XX:+UseContainerSupport` respeta los límites de CPU del contenedor; `-XX:MaxRAMPercentage=75` usa hasta el 75% de la RAM asignada.

### Comando de build (desde raíz del repo)
```bash
# Ejemplo: auth-service
docker build -f services/circleguard-auth-service/Dockerfile . -t circleguard/auth-service:dev
```

---

## 4. Tarea 2: Kubernetes

### Estructura de ambientes

| Ambiente | Namespace | Réplicas | Uso |
|---|---|---|---|
| dev | cg-dev | 1 | CI automático en cada push |
| stage | cg-stage | 1 | Validación pre-producción |
| master | cg-master | 2 | Producción con alta disponibilidad |

### Recursos por servicio
| Servicio | RAM Request | RAM Limit | CPU Request | CPU Limit |
|---|---|---|---|---|
| promotion-service | 512Mi | 1Gi | 250m | 500m |
| gateway-service | 256Mi | 512Mi | 150m | 500m |
| auth/identity/notification/form | 256Mi | 512Mi | 100m | 300m |

### Health Probes
- **readinessProbe**: `GET /actuator/health/readiness` — initialDelay 30s
- **livenessProbe**: `GET /actuator/health/liveness` — initialDelay 60s

---

## 5. Tarea 3: Jenkinsfiles

### Pipeline Dev (`Jenkinsfile.dev`)
```
Checkout → Unit Tests (paralelo) → Build JARs (paralelo) → 
Docker Build (paralelo) → Deploy cg-dev → Rollout Status → Smoke Test
```

### Pipeline Stage (`Jenkinsfile.stage`)
```
Checkout → Unit Tests → Build JARs → Docker Build → Deploy cg-stage → 
Integration Tests (Testcontainers) → E2E Tests (pytest+playwright) → 
Performance Baseline (Locust 30u/2min)
```

### Pipeline Master (`Jenkinsfile.master`)
```
Checkout → Unit Tests → Integration Tests → Build JARs → Docker Build & Tag →
Validate STAGE Health → Smoke Tests on STAGE → 
[input() Approval Gate — tech-lead] →
Deploy cg-master (2 réplicas) → Rollout Status → Smoke Tests on MASTER →
Generate Release Notes → git tag v{VERSION} → archiveArtifacts
```

**Puntos críticos del diseño:**
- Los 6 módulos Gradle se compilan y prueban en **paralelo** (`parallel {}`)
- El comando Docker siempre usa `-f services/{svc}/Dockerfile .` (punto = raíz)
- El `input()` gate tiene timeout de 24h para no bloquear indefinidamente
- Los Release Notes parsean Conventional Commits: `feat:`, `fix:`, `perf:`, `refactor:`

---

## 6. Tarea 4: Pruebas Unitarias

### `AuthServiceTest.kt` — auth-service
| Test | Verifica |
|---|---|
| `login local con credenciales válidas retorna JWT válido` | Token empieza con "eyJ", tiene 3 partes |
| `credenciales inválidas lanzan excepción` | Clave incorrecta no puede parsear token |
| `usuario no encontrado retorna Optional vacío` | Repositorio retorna empty() |
| `generateToken incluye anonymousId como subject` | Subject = UUID, no nombre real |
| `generateToken incluye permissions en claims` | Array "permissions" con roles |

### `StatusPromotionServiceTest.kt` — promotion-service
| Test | Verifica |
|---|---|
| `sospechoso con contacto confirmado es promovido a PROBABLE` | Neo4j query ejecutado |
| `sin contactos confirmados, Kafka no recibe evento` | `verifyNoInteractions(kafkaTemplate)` |
| `ventana de 14 días calculada como milisegundos` | Threshold = 14 × 24 × 60 × 60 × 1000 ms |
| `cambio de estado invalida clave Redis` | `multiSet` no llamado sin usuarios |
| `contacto fuera de ventana 14 días, sin transición` | Sin eventos Kafka |

### `NotificationServiceTest.kt` — notification-service
| Test | Verifica |
|---|---|
| `evento CONFIRMED dispara email` | `emailService.sendAsync(userId, content)` invocado |
| `evento CONFIRMED invoca SmsService` | `smsService.sendAsync(userId, sms)` invocado |
| `dispatch invoca 3 canales en paralelo` | email + sms + push verificados |
| `estado ACTIVE no dispara notificaciones` | shouldDispatch = false |
| `plantilla no contiene nombre real` | Sin "nombre", sin "@", con anonId |

### `FormServiceTest.kt` — form-service
| Test | Verifica |
|---|---|
| `formulario con síntomas se guarda en repositorio` | `repository.save()` invocado |
| `al enviar, publica en Kafka survey.submitted` | `kafkaTemplate.send("survey.submitted", ...)` |
| `adjunto en formulario → estado PENDING` | `result.validationStatus == PENDING` |
| `validar como APPROVED publica certificate.validated` | `kafkaTemplate.send("certificate.validated", ...)` |
| `validar como REJECTED NO publica en Kafka` | `verifyNoInteractions(kafkaTemplate)` |

---

## 7. Tarea 5: Pruebas de Integración (Testcontainers)

### `PromotionIntegrationTest.kt`
- **@Testcontainers**: levanta PostgreSQL 16 y Neo4j 5.26 en contenedores Docker reales
- **@DynamicPropertySource**: inyecta las URLs dinámicas de los contenedores en SpringBoot antes de inicializar el contexto
- Verifica: contexto Spring, salud del sistema, seguridad JWT, conectividad BD

### `FormPromotionIntegrationTest.kt`
- Usa **WireMock** como servidor stub para simular las respuestas de promotion-service
- Verifica que el contrato REST entre form-service y promotion-service es correcto
- Evita la necesidad de levantar ambos servicios simultáneamente

---

## 8. Tarea 6: Pruebas E2E (pytest + playwright)

```bash
pip install pytest playwright requests pytest-html
playwright install chromium
pytest tests/e2e/ -v --html=report.html
```

| Test | Descripción |
|---|---|
| `test_login_and_access_protected_resource` | Login → JWT "eyJ..." → gateway responde |
| `test_symptom_form_triggers_status_promotion` | Form submit → wait 3s → estado actualizado |
| `test_confirmed_status_triggers_notification` | notification-service health + alertas |
| `test_expired_qr_token` | Token inválido → 401/403 del gateway |
| `test_identity_service_does_not_expose_real_name` | Sin "name", "email", "@" en respuesta |

---

## 9. Tarea 7: Pruebas de Rendimiento (Locust)

### Escenario de Carga
- **Usuarios**: 80 | **Spawn**: 10/s | **Duración**: 5 minutos
- Simula tráfico normal de entrada al campus

### Escenario de Estrés
- **Usuarios**: 200 | **Spawn**: 20/s | **Duración**: 3 minutos
- Simula pico de acceso masivo (ingreso simultáneo de estudiantes)

### Criterios de Fallo
```python
avg_response_time > 800ms  → FAIL
fail_ratio > 2%             → FAIL
```

### Ejecutar
```bash
bash tests/performance/run_locust.sh --scenario all
# Reportes en: reports/{timestamp}/load-report.html
```

---

## 10. Análisis de Riesgos CI/CD

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| Neo4j en Testcontainers lento | Alta | Medio | Timeout de 120s en rollout |
| Kafka no disponible en tests | Media | Bajo | `spring.kafka.bootstrap-servers=localhost:9093` |
| Aprobación manual > 24h | Baja | Alto | Timeout en `input()` + notificación Slack |
| Imagen JVM > 500MB | Media | Bajo | JRE Alpine (~100MB base) |

---

## 11. Comandos de Referencia

```bash
# Ejecutar todos los tests de un servicio
./gradlew :services:circleguard-auth-service:test

# Compilar JAR de producción
./gradlew :services:circleguard-promotion-service:bootJar -x test --no-daemon

# Build Docker (contexto = raíz del repo)
docker build -f services/circleguard-gateway-service/Dockerfile . -t cg/gateway:dev

# Desplegar en K8s
kubectl apply -f k8s/dev/ --recursive

# Verificar rollout
kubectl rollout status deployment/auth-service -n cg-dev --timeout=120s

# Locust rápido (prueba manual)
locust -f tests/performance/locustfile.py --headless --users 10 --spawn-rate 2 \
  --run-time 1m --host http://localhost:8087
```
