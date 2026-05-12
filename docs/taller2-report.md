# Reporte Técnico Taller 2: Pruebas y Lanzamiento — CircleGuard

## 1. Configuración de Pipelines de CI/CD

Se han implementado tres pipelines declarativos en Jenkins para automatizar el ciclo de vida del software:

- **Jenkinsfile.dev**: Ejecuta pruebas unitarias, construye imágenes de Docker con el tag `dev-${BUILD_NUMBER}` y despliega en el namespace `cg-dev` de Kubernetes.
- **Jenkinsfile.stage**: Además de lo anterior, ejecuta pruebas de integración y E2E antes de desplegar en `cg-stage`.
- **Jenkinsfile.master**: Es el pipeline de producción. Incluye pruebas de rendimiento con Locust y genera notas de lanzamiento (Release Notes) automáticamente antes del despliegue en `cg-master`.

## 2. Estrategia de Pruebas

El sistema cuenta con una cobertura multinivel:

- **Pruebas Unitarias**: Validan la lógica de negocio aislada (ej. `JwtTokenServiceTest`, `QrTokenServiceTest`).
- **Pruebas de Integración**: Validan la comunicación entre componentes (ej. `AuthToIdentityIntegrationTest`) y la integración con infraestructura mediante **Testcontainers** y simuladores de eventos (Kafka/Redis).
- **Pruebas E2E (End-to-End)**: Validan flujos de usuario completos (ej. `LoginFlowE2ETest`, `SurveySubmissionFlowE2ETest`) interactuando con las APIs REST de los servicios.

## 3. Pruebas de Rendimiento (Locust)

Se han simulado los escenarios críticos de uso:
- **Login de Usuarios**: Carga base de estudiantes accediendo al sistema.
- **Validación de QR**: Escenario de alta carga en puntos de acceso al campus.
- **Reporte de Síntomas**: Procesamiento asíncrono de encuestas de salud.

**Métricas Objetivo:**
- Latencia promedio: < 500ms para validación de tokens.
- Capacidad: Soportar 50-100 usuarios concurrentes en escenarios de pico.

## 4. Instrucciones de Reproducción

### Local
1. Subir infraestructura base: `docker-compose -f docker-compose.dev.yml up -d`
2. Ejecutar todas las pruebas: `./gradlew test`
3. Ejecutar servicios en paralelo: `./gradlew bootRun --parallel`

### En Jenkins
1. Configurar credenciales: `dockerhub-credentials`, `github-credentials`, `kubeconfig`.
2. Crear un Multibranch Pipeline apuntando al repositorio.
3. El pipeline detectará los `Jenkinsfile.*` de acuerdo a la rama (`feature/*`, `release/*`, `master`).

---
**CircleGuard 팀 (Equipo de Desarrollo)**  
*Absoluta Privacidad. Alta Velocidad. Campus Seguro.*
