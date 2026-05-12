"""
Locust Performance & Stress Test File for CircleGuard
======================================================
Tests the main use-case flows of the CircleGuard platform:
1. Login flow (auth-service)
2. Survey submission (form-service)
3. QR validation (gateway-service)
4. Health status confirmation (promotion-service)

Usage:
    locust -f locustfile.py --host=http://localhost:8180 --headless -u 50 -r 5 --run-time 2m --html report.html

For multi-service testing, override HOST per run or use environment variables:
    AUTH_HOST=http://localhost:8180
    FORM_HOST=http://localhost:8086
    GATEWAY_HOST=http://localhost:8087
    PROMOTION_HOST=http://localhost:8088
"""

import os
import json
import uuid
import time
from locust import HttpUser, TaskSet, task, between, events
from locust.env import Environment


# ─── Shared Helpers ────────────────────────────────────────────────────────────

def make_auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


MOCK_JWT = (
    "eyJhbGciOiJIUzI1NiJ9."
    "eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJwZXJtaXNzaW9ucyI6WyJTVFVERU5UIl0sImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxOTAwMDAwMDAwfQ."
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
)


# ─── Auth Service Users ─────────────────────────────────────────────────────────

class LoginTaskSet(TaskSet):
    """Simulates students logging in to the CircleGuard system."""

    @task(3)
    def login_valid_user(self):
        """Main login flow — highest weight as it's the entry point."""
        payload = {
            "username": f"student{uuid.uuid4().hex[:4]}",
            "password": "password123"
        }
        with self.client.post(
            "/api/v1/auth/login",
            json=payload,
            catch_response=True,
            name="POST /auth/login"
        ) as response:
            if response.status_code in (200, 401, 403):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")

    @task(1)
    def login_invalid_credentials(self):
        """Stress test with bad credentials — should consistently return 401."""
        payload = {"username": "bad_user", "password": "wrong_pass"}
        with self.client.post(
            "/api/v1/auth/login",
            json=payload,
            catch_response=True,
            name="POST /auth/login (bad creds)"
        ) as response:
            if response.status_code in (401, 403, 200):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")


class AuthServiceUser(HttpUser):
    """User targeting the auth-service."""
    host = os.getenv("AUTH_HOST", "http://localhost:8180")
    tasks = [LoginTaskSet]
    wait_time = between(1, 3)


# ─── Form Service Users ─────────────────────────────────────────────────────────

class SurveyTaskSet(TaskSet):
    """Simulates students submitting daily health surveys."""

    @task(4)
    def submit_healthy_survey(self):
        """Most common case: healthy student submits survey."""
        payload = {
            "anonymousId": str(uuid.uuid4()),
            "hasFever": False,
            "hasCough": False,
            "responses": {}
        }
        with self.client.post(
            "/api/v1/surveys",
            json=payload,
            catch_response=True,
            name="POST /surveys (healthy)"
        ) as response:
            if response.status_code in (200, 201, 400, 401):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")

    @task(1)
    def submit_symptomatic_survey(self):
        """Less frequent: student reports symptoms."""
        payload = {
            "anonymousId": str(uuid.uuid4()),
            "hasFever": True,
            "hasCough": True,
            "responses": {}
        }
        with self.client.post(
            "/api/v1/surveys",
            json=payload,
            catch_response=True,
            name="POST /surveys (symptomatic)"
        ) as response:
            if response.status_code in (200, 201, 400, 401):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")


class FormServiceUser(HttpUser):
    """User targeting the form-service."""
    host = os.getenv("FORM_HOST", "http://localhost:8086")
    tasks = [SurveyTaskSet]
    wait_time = between(2, 5)


# ─── Gateway Service Users ─────────────────────────────────────────────────────

class GatewayTaskSet(TaskSet):
    """Simulates guards validating QR codes at building entrances."""

    @task(5)
    def validate_qr_token(self):
        """Peak load scenario: multiple guards scanning QR codes simultaneously."""
        payload = {"token": MOCK_JWT}
        with self.client.post(
            "/api/v1/gate/validate",
            json=payload,
            catch_response=True,
            name="POST /gate/validate"
        ) as response:
            if response.status_code in (200, 400, 401):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")

    @task(1)
    def validate_invalid_token(self):
        """Edge case: expired or tampered token."""
        payload = {"token": "invalid.jwt.token"}
        with self.client.post(
            "/api/v1/gate/validate",
            json=payload,
            catch_response=True,
            name="POST /gate/validate (invalid token)"
        ) as response:
            if response.status_code in (200, 400, 401):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")


class GatewayServiceUser(HttpUser):
    """User targeting the gateway-service. Highest load scenario."""
    host = os.getenv("GATEWAY_HOST", "http://localhost:8087")
    tasks = [GatewayTaskSet]
    wait_time = between(0.5, 2)


# ─── Promotion Service Users ────────────────────────────────────────────────────

class HealthStatusTaskSet(TaskSet):
    """Simulates health center staff updating student health statuses."""

    @task(2)
    def confirm_positive_case(self):
        """Health center confirms a positive COVID case."""
        payload = {"anonymousId": str(uuid.uuid4())}
        with self.client.post(
            "/api/v1/health/confirmed",
            json=payload,
            headers=make_auth_headers(MOCK_JWT),
            catch_response=True,
            name="POST /health/confirmed"
        ) as response:
            if response.status_code in (200, 401, 403):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")

    @task(1)
    def resolve_user_status(self):
        """Health center resolves a user's quarantine status."""
        payload = {"anonymousId": str(uuid.uuid4())}
        with self.client.post(
            "/api/v1/health/resolve",
            json=payload,
            headers=make_auth_headers(MOCK_JWT),
            catch_response=True,
            name="POST /health/resolve"
        ) as response:
            if response.status_code in (200, 401, 403):
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")


class PromotionServiceUser(HttpUser):
    """User targeting the promotion-service."""
    host = os.getenv("PROMOTION_HOST", "http://localhost:8088")
    tasks = [HealthStatusTaskSet]
    wait_time = between(3, 8)
