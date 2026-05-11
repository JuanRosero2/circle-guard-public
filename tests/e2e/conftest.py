"""
conftest.py — Configuración compartida para los tests E2E de CircleGuard
Provee fixtures de pytest que:
  1. Inicializan un token JWT válido haciendo login real contra auth-service
  2. Crean sesiones HTTP reutilizables para todos los tests
  3. Definen las URLs base de cada microservicio

Los tests requieren que todos los servicios estén corriendo localmente
(por ejemplo, vía docker-compose.dev.yml).
"""
import pytest
import requests
import time
from playwright.sync_api import sync_playwright, Browser, Page

# ── URLs base de cada servicio (localhost = entorno local o pod forwarding) ──
AUTH_URL       = "http://localhost:8180"
GATEWAY_URL    = "http://localhost:8087"
NOTIFICATION_URL = "http://localhost:8082"
IDENTITY_URL   = "http://localhost:8083"
PROMOTION_URL  = "http://localhost:8088"
FORM_URL       = "http://localhost:8086"

# Credenciales de prueba (existen gracias a V2__seed_test_users.sql + LDAP)
TEST_USERNAME  = "estudiante01"
TEST_PASSWORD  = "password123"
ADMIN_USERNAME = "admin01"
ADMIN_PASSWORD = "admin"


@pytest.fixture(scope="session")
def jwt_token() -> str:
    """
    Fixture de sesión: obtiene un JWT real de auth-service al inicio de la suite.
    Se reutiliza en todos los tests que necesiten autenticación.
    scope="session" significa que el login solo ocurre una vez por ejecución de pytest.
    """
    resp = requests.post(
        f"{AUTH_URL}/api/auth/login",
        json={"username": TEST_USERNAME, "password": TEST_PASSWORD, "type": "local"},
        timeout=10
    )
    assert resp.status_code == 200, f"Login falló: {resp.status_code} — {resp.text}"
    token = resp.json().get("token")
    assert token, "La respuesta de login debe contener un token JWT"
    return token


@pytest.fixture(scope="session")
def auth_headers(jwt_token) -> dict:
    """Cabeceras HTTP con el JWT Bearer para llamadas autenticadas."""
    return {"Authorization": f"Bearer {jwt_token}"}


@pytest.fixture(scope="session")
def http_session(auth_headers) -> requests.Session:
    """Sesión requests reutilizable con cabeceras de autenticación."""
    session = requests.Session()
    session.headers.update(auth_headers)
    return session


@pytest.fixture(scope="session")
def browser_page():
    """
    Fixture de Playwright: lanza un browser Chromium headless para tests de UI.
    scope="session" para reutilizar el browser en toda la suite.
    """
    with sync_playwright() as p:
        browser: Browser = p.chromium.launch(headless=True)
        page: Page = browser.new_page()
        yield page
        browser.close()
