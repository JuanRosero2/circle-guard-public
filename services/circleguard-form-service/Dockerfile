# ============================================================
# Stage 1: Builder — Monorepo Gradle, contexto = raíz del repo
# ============================================================
FROM openjdk:17-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY services/circleguard-form-service/ services/circleguard-form-service/

RUN chmod +x gradlew
RUN ./gradlew :services:circleguard-form-service:bootJar -x test --no-daemon

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM openjdk:17-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/services/circleguard-form-service/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8086/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75", \
    "-jar", "app.jar"]
