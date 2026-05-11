# ============================================================
# Stage 1: Builder — Compila el JAR desde la raíz del monorepo
# El contexto de build SIEMPRE es la raíz del repositorio (.)
# para que Gradle pueda resolver el proyecto multi-módulo.
# ============================================================
FROM ghcr.io/juanrosero2/openjdk:17-jdk-alpine AS builder

WORKDIR /workspace

# Copiar wrapper de Gradle y archivos de configuración del monorepo
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copiar únicamente el source del servicio que vamos a compilar
COPY services/circleguard-auth-service/ services/circleguard-auth-service/

# Dar permisos de ejecución al wrapper
RUN chmod +x gradlew

# Compilar solo el módulo auth-service, sin tests, sin daemon
# --no-daemon evita que el proceso quede colgado dentro del contenedor
RUN ./gradlew :services:circleguard-auth-service:bootJar -x test --no-daemon

# ============================================================
# Stage 2: Runtime — Imagen mínima de producción
# ============================================================
FROM ghcr.io/juanrosero2/openjdk:17-jdk-alpine AS runtime

# Crear grupo y usuario no-root para ejecutar la aplicación
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copiar el JAR compilado desde el stage builder
COPY --from=builder /workspace/services/circleguard-auth-service/build/libs/*.jar app.jar

# Cambiar propietario del directorio a usuario no-root
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8180

# HEALTHCHECK usando wget (incluido en Alpine) contra el endpoint de Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8180/actuator/health || exit 1

# JVM flags: UseContainerSupport respeta los límites de CPU/RAM del contenedor
# MaxRAMPercentage=75 usa hasta el 75% de la RAM asignada al contenedor
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75", \
    "-jar", "app.jar"]
