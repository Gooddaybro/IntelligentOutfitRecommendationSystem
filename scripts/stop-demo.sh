#!/usr/bin/env sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="${ENV_FILE:-$PROJECT_DIR/.env}"
PYTHON_AI_CONTEXT="${PYTHON_AI_CONTEXT:-$PROJECT_DIR/../AI-Clothing-Shopping-Assistant-System}"

if [ ! -f "$ENV_FILE" ]; then
  ENV_FILE="$PROJECT_DIR/.env.demo.example"
fi

export PYTHON_AI_CONTEXT

docker compose --env-file "$ENV_FILE" -f "$PROJECT_DIR/docker-compose.yml" -f "$PROJECT_DIR/docker-compose.demo.yml" down "$@"
