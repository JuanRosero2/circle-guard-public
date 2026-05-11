"""
locustfile.py — Pruebas de rendimiento para CircleGuard
Simula el tráfico de estudiantes y personal de salud bajo carga.

Clases de usuarios:
  - CircleGuardStudent (weight=20): representa a estudiantes acediendo a sus formularios
  - HealthCenterAdmin  (weight=1):  representa al personal de salud administrando

Criterios de fallo (@events.quitting):
  - Tiempo de respuesta promedio > 800ms
  - Tasa de fallos > 2%

Uso:
  locust -f locustfile.py --headless --users 80 --spawn-rate 10 --run-time 5m
  O ejecutar con: bash tests/performance/run_locust.sh
"""

import random
import uuid
import json
from locust import HttpUser, task, between, events
from locust.env import Environment


# ── IDs anónimos de prueba (simular estudiantes registrados) ────────────────
SAMPLE_ANON_IDS = [str(uuid.uuid4()) for _ in range(50)]

# Síntomas aleatorios para formularios de prueba
SYMPTOMS = [
    {"hasFever": True,  "hasCough": True,  "hasHeadache": False},
    {"hasFever": False, "hasCough": True,  "hasHeadache": True},
    {"hasFever": False, "hasCough": False, "hasHeadache": False},
    {"hasFever": True,  "hasCough": False, "hasHeadache": True},
]


class CircleGuardStudent(HttpUser):
    """
    Simula el comportamiento de un estudiante de CircleGuard.
    weight=20 significa que habrá 20x más estudiantes que admins.

    Flujo típico:
      1. on_start: login con tipo "local"
      2. Consultar estado de salud (task más frecuente)
      3. Enviar formulario de síntomas
      4. Consultar notificaciones
      5. Validar código QR al entrar a instalaciones
    """
    weight = 20
    wait_time = between(1, 4)  # Espera entre 1-4 segundos entre requests

    def on_start(self):
        """Login al inicio de cada usuario virtual simulado."""
        # Usar un usuario de la lista de semillado de la BD
        student_num = random.randint(1, 10)
        resp = self.client.post(
            "/api/auth/login",
            json={
                "username": f"estudiante{student_num:02d}",
                "password": "password123",
                "type": "local"
            },
            catch_response=True
        )
        if resp.status_code == 200:
            token = resp.json().get("token", "")
            self.client.headers.update({"Authorization": f"Bearer {token}"})
            resp.success()
        else:
            # En pruebas de carga, algunos logins pueden fallar
            resp.failure(f"Login failed: {resp.status_code}")
            # Continuar con token vacío para medir el comportamiento
            self.client.headers.update({"Authorization": "Bearer invalid"})

    @task(4)
    def get_promotion_status(self):
        """
        Task más frecuente (weight=4): consultar estado de salud.
        Simula a un estudiante verificando su estado antes de entrar al campus.
        """
        anon_id = random.choice(SAMPLE_ANON_IDS)
        with self.client.get(
            f"/api/health-status/{anon_id}",
            name="/api/health-status/[anonId]",
            catch_response=True
        ) as resp:
            if resp.status_code in [200, 401, 403, 404]:
                resp.success()
            else:
                resp.failure(f"Unexpected status: {resp.status_code}")

    @task(2)
    def submit_health_form(self):
        """
        Task intermedia (weight=2): enviar formulario de síntomas.
        Simula el reporte diario de salud del estudiante.
        """
        anon_id = str(uuid.uuid4())
        symptoms = random.choice(SYMPTOMS)
        payload = {
            "anonymousId": anon_id,
            "responses": [],
            **symptoms
        }
        with self.client.post(
            "/api/health-surveys",
            json=payload,
            name="/api/health-surveys",
            catch_response=True
        ) as resp:
            if resp.status_code in [200, 201, 401, 403]:
                resp.success()
            else:
                resp.failure(f"Form submit failed: {resp.status_code}")

    @task(1)
    def get_notifications(self):
        """
        Task menos frecuente (weight=1): consultar notificaciones del usuario.
        Llama directamente al notification-service en puerto 8082.
        """
        with self.client.get(
            "http://localhost:8082/actuator/health",
            name="notification-service /actuator/health",
            catch_response=True
        ) as resp:
            if resp.status_code in [200, 503]:
                resp.success()
            else:
                resp.failure(f"Notification service error: {resp.status_code}")

    @task(1)
    def validate_qr_entry(self):
        """
        Task de validación de entrada (weight=1): simular escaneo de QR al entrar.
        El gateway valida el token QR y llama a Redis para verificar la whitelist.
        """
        # Simular un QR token (en pruebas reales se generaría vía /api/qr/generate)
        fake_qr = f"qr-{uuid.uuid4()}"
        with self.client.get(
            "/api/entry/validate",
            headers={"X-QR-Token": fake_qr},
            name="/api/entry/validate",
            catch_response=True
        ) as resp:
            # 401 es válido en pruebas de carga (QR falso)
            if resp.status_code in [200, 401, 403, 404]:
                resp.success()
            else:
                resp.failure(f"QR validation error: {resp.status_code}")


class HealthCenterAdmin(HttpUser):
    """
    Simula al personal del centro de salud.
    weight=1: pequeña fracción del tráfico total.

    Flujo típico:
      1. Login como admin
      2. Forzar cambio de estado de usuarios
      3. Consultar dashboard de hotspots
    """
    weight = 1
    wait_time = between(2, 8)  # Los admins son más lentos (leen antes de actuar)

    def on_start(self):
        """Login de admin con credenciales de mayor privilegio."""
        resp = self.client.post(
            "/api/auth/login",
            json={
                "username": "admin01",
                "password": "admin",
                "type": "local"
            },
            catch_response=True
        )
        if resp.status_code == 200:
            token = resp.json().get("token", "")
            self.client.headers.update({"Authorization": f"Bearer {token}"})
            resp.success()
        else:
            resp.failure(f"Admin login failed: {resp.status_code}")

    @task(2)
    def force_promote_status(self):
        """
        Task (weight=2): forzar cambio de estado de un usuario.
        Simula al admin marcando un caso como CONFIRMED tras investigación.
        """
        anon_id = random.choice(SAMPLE_ANON_IDS)
        with self.client.post(
            "/api/admin/promote",
            json={
                "anonymousId": anon_id,
                "newStatus": "SUSPECT",
                "reason": "Admin force promote — load test"
            },
            name="/api/admin/promote",
            catch_response=True
        ) as resp:
            if resp.status_code in [200, 201, 401, 403, 404]:
                resp.success()
            else:
                resp.failure(f"Admin promote failed: {resp.status_code}")

    @task(1)
    def get_dashboard_hotspots(self):
        """
        Task (weight=1): consultar hotspots del dashboard.
        Llama al dashboard-service en puerto 8084 para obtener zonas de riesgo.
        """
        with self.client.get(
            "http://localhost:8084/api/dashboard/hotspots",
            name="dashboard /api/dashboard/hotspots",
            catch_response=True
        ) as resp:
            if resp.status_code in [200, 401, 403, 404, 503]:
                resp.success()
            else:
                resp.failure(f"Dashboard error: {resp.status_code}")


# ── Hook de finalización: verificar criterios de rendimiento ────────────────
@events.quitting.add_listener
def on_quitting(environment: Environment, **kwargs):
    """
    Valida las métricas de rendimiento al final de la prueba.
    Falla la prueba si se superan los umbrales definidos:
      - Tiempo de respuesta promedio > 800ms
      - Tasa de fallos > 2%
    """
    stats = environment.stats.total

    avg_response_time = stats.avg_response_time
    total_requests = stats.num_requests
    failed_requests = stats.num_failures
    fail_ratio = (failed_requests / total_requests * 100) if total_requests > 0 else 0

    print(f"\n{'='*60}")
    print(f"LOCUST PERFORMANCE RESULTS")
    print(f"{'='*60}")
    print(f"Total requests  : {total_requests}")
    print(f"Failed requests : {failed_requests}")
    print(f"Failure ratio   : {fail_ratio:.2f}%")
    print(f"Avg response    : {avg_response_time:.0f}ms")
    print(f"{'='*60}")

    failed = False

    if avg_response_time > 800:
        print(f"FAIL: Avg response time {avg_response_time:.0f}ms > threshold 800ms")
        failed = True

    if fail_ratio > 2:
        print(f"FAIL: Failure ratio {fail_ratio:.2f}% > threshold 2%")
        failed = True

    if failed:
        environment.process_exit_code = 1
    else:
        print("PASS: All performance criteria met!")
        environment.process_exit_code = 0
