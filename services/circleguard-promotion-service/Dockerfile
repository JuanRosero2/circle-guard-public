# ============================================================
# Stage 1: Builder — Monorepo Gradle, contexto = raíz del repo
# promotion-service es el más pesado: Neo4j client + Kafka + Redis
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY services/circleguard-promotion-service/ services/circleguard-promotion-service/

RUN chmod +x gradlew
RUN ./gradlew :services:circleguard-promotion-service:bootJar -x test --no-daemon

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/services/circleguard-promotion-service/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8088/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75", \
    "-jar", "app.jar"]
