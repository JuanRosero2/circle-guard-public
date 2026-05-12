# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY services/circleguard-notification-service/build.gradle.kts services/circleguard-notification-service/

RUN chmod +x gradlew

COPY services/circleguard-notification-service/src services/circleguard-notification-service/src

RUN ./gradlew :services:circleguard-notification-service:bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S circleguard && adduser -S circleguard -G circleguard

COPY --from=builder /workspace/services/circleguard-notification-service/build/libs/*.jar app.jar

RUN chown circleguard:circleguard app.jar
USER circleguard

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
