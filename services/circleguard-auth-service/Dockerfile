# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Gradle wrapper and root build files
COPY gradlew .
COPY gradle gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY services/circleguard-auth-service/build.gradle.kts services/circleguard-auth-service/

RUN chmod +x gradlew

# Copy source code
COPY services/circleguard-auth-service/src services/circleguard-auth-service/src

# Build the JAR (skip tests - tests run in pipeline)
RUN ./gradlew :services:circleguard-auth-service:bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S circleguard && adduser -S circleguard -G circleguard

COPY --from=builder /workspace/services/circleguard-auth-service/build/libs/*.jar app.jar

RUN chown circleguard:circleguard app.jar
USER circleguard

EXPOSE 8180
ENTRYPOINT ["java", "-jar", "app.jar"]
