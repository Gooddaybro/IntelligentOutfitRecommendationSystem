#!/usr/bin/env sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="${ENV_FILE:-$PROJECT_DIR/.env}"
DRY_RUN=false

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

if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=true
fi

if [ ! -f "$ENV_FILE" ]; then
  ENV_FILE="$PROJECT_DIR/.env.demo.example"
fi

if [ ! -f "$PYTHON_AI_CONTEXT/Dockerfile" ]; then
  echo "Python AI Dockerfile not found: $PYTHON_AI_CONTEXT/Dockerfile" >&2
  echo "Clone the Python repository beside this repository or set PYTHON_AI_CONTEXT." >&2
  exit 1
fi

export PYTHON_AI_CONTEXT

echo "Using env file: $ENV_FILE"
echo "Using Python AI context: $PYTHON_AI_CONTEXT"

if [ "$DRY_RUN" = "true" ]; then
  echo "DRY RUN: docker compose --env-file $ENV_FILE -f $PROJECT_DIR/docker-compose.yml -f $PROJECT_DIR/docker-compose.demo.yml up -d --build"
  exit 0
fi

set +e
docker compose --env-file "$ENV_FILE" -f "$PROJECT_DIR/docker-compose.yml" -f "$PROJECT_DIR/docker-compose.demo.yml" up -d --build
STATUS=$?
set -e

if [ "$STATUS" -ne 0 ]; then
  cat >&2 <<'EOF'

Demo startup failed.

If the error mentions Docker Hub, docker.elastic.co, auth token, TLS handshake,
EOF, or timeout, this is usually a registry/network issue rather than an
application build issue.

Edit .env and override these image variables with reachable mirrors or
pre-pulled private-registry images:

  MYSQL_IMAGE
  REDIS_IMAGE
  RABBITMQ_IMAGE
  LANGGRAPH_POSTGRES_IMAGE
  ELASTICSEARCH_IMAGE
  KIBANA_IMAGE
  CURL_IMAGE
  JDK_BASE_IMAGE
  JRE_BASE_IMAGE
  NODE_BASE_IMAGE
  NGINX_BASE_IMAGE
  PYTHON_BASE_IMAGE

For dependency downloads inside images, override:

  MAVEN_REPO_URL
  PIP_INDEX_URL
  PIP_TRUSTED_HOST

Kibana is optional. Start it only when needed with:

  COMPOSE_PROFILES=observability sh scripts/start-demo.sh

EOF
  exit "$STATUS"
fi
