#!/usr/bin/env bash
# ==============================================================================
# run_locust.sh — Script para ejecutar pruebas de rendimiento de CircleGuard
# Escenario 1 (Carga):   80 usuarios, spawn 10/s, 5 minutos
# Escenario 2 (Estrés): 200 usuarios, spawn 20/s, 3 minutos
#
# Uso:
#   bash tests/performance/run_locust.sh
#   bash tests/performance/run_locust.sh --scenario stress
#
# Prerrequisitos:
#   pip install locust
#   Todos los servicios CircleGuard corriendo en localhost
# ==============================================================================
set -euo pipefail

# Directorio donde se guardan los reportes (ordenados por timestamp)
REPORT_DIR="reports/$(date '+%Y%m%d_%H%M%S')"
mkdir -p "$REPORT_DIR"

LOCUSTFILE="tests/performance/locustfile.py"
HOST="http://localhost:8087"   # El gateway-service es el host principal

echo "============================================================"
echo "CircleGuard Performance Tests — $(date '+%Y-%m-%d %H:%M:%S')"
echo "Host: $HOST"
echo "Reports: $REPORT_DIR"
echo "============================================================"

# Detectar el escenario a ejecutar (argumento opcional)
SCENARIO="${1:---scenario load}"

# ── Función para ejecutar Locust ────────────────────────────────────────────
run_locust() {
    local scenario_name="$1"
    local users="$2"
    local spawn_rate="$3"
    local run_time="$4"

    echo ""
    echo "▶ Iniciando escenario: $scenario_name"
    echo "  Usuarios: $users | Spawn rate: $spawn_rate/s | Duración: $run_time"
    echo ""

    locust \
        -f "$LOCUSTFILE" \
        --headless \
        --users "$users" \
        --spawn-rate "$spawn_rate" \
        --run-time "$run_time" \
        --host "$HOST" \
        --html "$REPORT_DIR/${scenario_name}-report.html" \
        --csv "$REPORT_DIR/${scenario_name}" \
        --only-summary \
        2>&1 | tee "$REPORT_DIR/${scenario_name}-output.log"

    local exit_code=$?
    if [ $exit_code -eq 0 ]; then
        echo "✅ Escenario '$scenario_name' PASADO"
    else
        echo "❌ Escenario '$scenario_name' FALLIDO (exit code: $exit_code)"
    fi

    echo "   Reporte HTML: $REPORT_DIR/${scenario_name}-report.html"
    echo "   Reporte CSV:  $REPORT_DIR/${scenario_name}_stats.csv"
    return $exit_code
}

# ── Escenario 1: Carga (80 usuarios, 5 minutos) ────────────────────────────
# Simula el tráfico normal en horas de entrada/salida del campus
if [ "$SCENARIO" = "--scenario load" ] || [ "$SCENARIO" = "--scenario all" ]; then
    echo ""
    echo "═══════ ESCENARIO 1: CARGA NORMAL ═══════"
    run_locust "load" 80 10 "5m" || LOAD_FAILED=true
fi

# ── Escenario 2: Estrés (200 usuarios, 3 minutos) ─────────────────────────
# Simula un pico de acceso (ej: hora de ingreso masivo de estudiantes)
if [ "$SCENARIO" = "--scenario stress" ] || [ "$SCENARIO" = "--scenario all" ]; then
    echo ""
    echo "═══════ ESCENARIO 2: ESTRÉS ═══════"
    run_locust "stress" 200 20 "3m" || STRESS_FAILED=true
fi

# ── Resumen final ──────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "RESUMEN DE PRUEBAS DE RENDIMIENTO"
echo "============================================================"
echo "Directorio de reportes: $REPORT_DIR"
ls -la "$REPORT_DIR/"

# Mostrar estadísticas clave del CSV si existe
STATS_CSV="$REPORT_DIR/load_stats.csv"
if [ -f "$STATS_CSV" ]; then
    echo ""
    echo "── Estadísticas de carga (primeras 10 líneas): ──"
    head -10 "$STATS_CSV"
fi

# Fallar el script si algún escenario falló
if [ "${LOAD_FAILED:-false}" = "true" ] || [ "${STRESS_FAILED:-false}" = "true" ]; then
    echo ""
    echo "❌ UNO O MÁS ESCENARIOS FALLARON — Ver logs arriba"
    exit 1
fi

echo ""
echo "✅ TODAS LAS PRUEBAS DE RENDIMIENTO PASARON"
exit 0
