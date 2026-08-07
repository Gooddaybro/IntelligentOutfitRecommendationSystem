#!/usr/bin/env sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="${ENV_FILE:-$PROJECT_DIR/.env}"
PYTHON_AI_CONTEXT="${PYTHON_AI_CONTEXT:-$PROJECT_DIR/../AI-Clothing-Shopping-Assistant-System}"
DRY_RUN=false

if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=true
fi

if [ ! -f "$ENV_FILE" ]; then
  ENV_FILE="$PROJECT_DIR/.env.demo.example"
fi

if [ ! -f "$PYTHON_AI_CONTEXT/Dockerfile" ]; then
  echo "Python AI Dockerfile not found: $PYTHON_AI_CONTEXT/Dockerfile" >&2
  echo "Clone AI-Clothing-Shopping-Assistant-System beside this repository or set PYTHON_AI_CONTEXT." >&2
  exit 1
fi

export PYTHON_AI_CONTEXT

echo "Using env file: $ENV_FILE"
echo "Using Python AI context: $PYTHON_AI_CONTEXT"

if [ "$DRY_RUN" = "true" ]; then
  echo "DRY RUN: docker compose --env-file $ENV_FILE -f $PROJECT_DIR/docker-compose.yml -f $PROJECT_DIR/docker-compose.demo.yml up -d --build"
  exit 0
fi

docker compose --env-file "$ENV_FILE" -f "$PROJECT_DIR/docker-compose.yml" -f "$PROJECT_DIR/docker-compose.demo.yml" up -d --build
