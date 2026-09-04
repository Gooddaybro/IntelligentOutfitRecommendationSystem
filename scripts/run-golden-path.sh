#!/usr/bin/env sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="${ENV_FILE:-$PROJECT_DIR/.env.demo.example}"
ARTIFACT_DIR="${GOLDEN_PATH_ARTIFACT_DIR:-$PROJECT_DIR/artifacts/golden-path}"
KEEP_STACK="${GOLDEN_PATH_KEEP_STACK:-false}"

if [ -z "${PYTHON_AI_CONTEXT:-}" ]; then
  for candidate in \
    "$PROJECT_DIR/../AI Clothing Shopping Assistant System" \
    "$PROJECT_DIR/../AI-Clothing-Shopping-Assistant-System"
  do
    if [ -f "$candidate/Dockerfile" ]; then
      PYTHON_AI_CONTEXT=$candidate
      break
    fi
  done
fi
PYTHON_AI_CONTEXT="${PYTHON_AI_CONTEXT:-$PROJECT_DIR/../AI Clothing Shopping Assistant System}"

if [ ! -f "$PYTHON_AI_CONTEXT/Dockerfile" ]; then
  echo "Python AI Dockerfile not found: $PYTHON_AI_CONTEXT/Dockerfile" >&2
  exit 1
fi

export PYTHON_AI_CONTEXT
export AI_RUNTIME_ENV=integration
export AI_DETERMINISTIC_PROVIDER=true
export MYSQL_HOST_PORT="${MYSQL_HOST_PORT:-0}"
export REDIS_HOST_PORT="${REDIS_HOST_PORT:-0}"
export RABBITMQ_AMQP_HOST_PORT="${RABBITMQ_AMQP_HOST_PORT:-0}"
export RABBITMQ_MANAGEMENT_HOST_PORT="${RABBITMQ_MANAGEMENT_HOST_PORT:-0}"
export RABBITMQ_PROMETHEUS_HOST_PORT="${RABBITMQ_PROMETHEUS_HOST_PORT:-0}"
export LANGGRAPH_POSTGRES_HOST_PORT="${LANGGRAPH_POSTGRES_HOST_PORT:-0}"
export ELASTICSEARCH_HOST_PORT="${ELASTICSEARCH_HOST_PORT:-0}"
export PYTHON_AI_HOST_PORT="${PYTHON_AI_HOST_PORT:-0}"
export JAVA_BACKEND_HOST_PORT="${JAVA_BACKEND_HOST_PORT:-0}"

compose() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$PROJECT_DIR/docker-compose.yml" \
    -f "$PROJECT_DIR/docker-compose.demo.yml" \
    "$@"
}

collect_failure_evidence() {
  mkdir -p "$ARTIFACT_DIR"
  compose ps --all > "$ARTIFACT_DIR/compose-ps.txt" 2>&1 || true
  compose logs --no-color 2>&1 \
    | python "$PROJECT_DIR/scripts/sanitize_logs.py" \
    > "$ARTIFACT_DIR/compose.log" || true
}

cleanup() {
  status=$?
  trap - EXIT INT TERM
  if [ "$status" -ne 0 ]; then
    collect_failure_evidence
  fi
  if [ "$KEEP_STACK" != "true" ]; then
    compose down --volumes --remove-orphans || true
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

rm -rf "$ARTIFACT_DIR"
compose down --volumes --remove-orphans
compose config --quiet
compose up -d --build --wait --wait-timeout "${GOLDEN_PATH_WAIT_SECONDS:-600}"
npm --prefix "$PROJECT_DIR/frontend" run test:e2e:integration
