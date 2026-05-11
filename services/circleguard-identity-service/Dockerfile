# ============================================================
# Stage 1: Builder — Monorepo Gradle, contexto = raíz del repo
# ============================================================
FROM ghcr.io/juanrosero2/openjdk:17-jdk-alpine AS builder
WORKDIR /workspace
# Copiar wrapper de Gradle y archivos de configuración del monorepo
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
# Copiar únicamente el source del servicio que vamos a compilar
COPY services/circleguard-identity-service/ services/circleguard-identity-service/
# Dar permisos de ejecución al wrapper
RUN chmod +x gradlew
RUN ./gradlew :services:circleguard-identity-service:bootJar -x test --no-daemon

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM ghcr.io/juanrosero2/openjdk:17-jdk-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/services/circleguard-identity-service/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75", \
    "-jar", "app.jar"]
