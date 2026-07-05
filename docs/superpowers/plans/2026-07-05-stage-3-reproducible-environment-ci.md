# Reproducible Environment And CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the multi-project workspace one predictable local setup path and one CI workflow that verifies shared contract, Python AI, Java backend, and frontend checks.

**Architecture:** Keep each project independent, but add a shallow root interface for developers: `.env.example`, `scripts/start-local-deps.sh`, `scripts/check-local.sh`, and root `.github/workflows/ci.yml`. Java runtime configuration becomes environment-variable driven with Docker Compose-compatible defaults.

**Tech Stack:** Bash, GitHub Actions, Java 21/Maven, Python unittest, Node 20/npm/Vitest, Docker Compose.

## Global Constraints

- Do not add MQ in Stage 3.
- Do not require real model provider keys to run tests.
- Keep secrets out of version control; `.env.example` may contain only non-secret local defaults.
- Preserve existing Java, Python, and frontend project boundaries.

---

## Files

- Create: `.env.example`
- Create: `.gitignore`
- Create: `README.md`
- Create: `scripts/start-local-deps.sh`
- Create: `scripts/check-local.sh`
- Create: `.github/workflows/ci.yml`
- Create: `tests/test_reproducible_environment.py`
- Modify: `IntelligentOutfitRecommendationSystem/docker-compose.yml`
- Modify: `IntelligentOutfitRecommendationSystem/backend/src/main/resources/application.properties`

## Task 1: Add Reproducibility Tests

**Interfaces:**
- Produces: `tests/test_reproducible_environment.py`
- Verifies: root env defaults, Java datasource defaults, script dry-run entrypoints, and root CI coverage

- [x] **Step 1: Write failing test**

```bash
python3 -m unittest tests.test_reproducible_environment -v
```

Expected before implementation: fails because `.env.example`, scripts, root CI, and Java 3307 defaults are missing.

## Task 2: Add Local Setup Interface

**Interfaces:**
- Produces: `.env.example`, `scripts/start-local-deps.sh`, `scripts/check-local.sh`, root `README.md`

- [x] **Step 1: Add non-secret local defaults**

The root `.env.example` defines MySQL on host port `3307`, Redis on `6379`, Java datasource values, Python base URL, and frontend API URL.

- [x] **Step 2: Add dependency startup script**

`sh scripts/start-local-deps.sh` starts MySQL and Redis using the shared env file and the existing Compose file.

- [x] **Step 3: Add local check script**

`sh scripts/check-local.sh` verifies the shared contract JSON, root reproducibility tests, Python tests, Java tests, and frontend tests/build.

## Task 3: Align Runtime Defaults

**Interfaces:**
- Produces: Java properties that can be overridden by environment variables

- [x] **Step 1: Update Java datasource defaults**

Default datasource URL now matches Compose: `localhost:3307/intelligent_outfit`.

- [x] **Step 2: Make Compose port configurable**

Compose uses `${MYSQL_HOST_PORT:-3307}:3306` and `${REDIS_HOST_PORT:-6379}:6379`.

## Task 4: Add Root CI

**Interfaces:**
- Produces: `.github/workflows/ci.yml`

- [x] **Step 1: Add shared contract check**

CI validates `outfit-project-contract/contracts/java-python-chat/v1.fields.json`.

- [x] **Step 2: Add project checks**

CI runs Python unit tests, Java backend tests, frontend unit tests, and frontend build.

## Task 5: Verification

- [x] **Step 1: Run root reproducibility tests**

```bash
python3 -m unittest tests.test_reproducible_environment -v
```

- [x] **Step 2: Run script dry-runs**

```bash
sh scripts/start-local-deps.sh --dry-run
sh scripts/check-local.sh --dry-run
```

- [x] **Step 3: Run Python, Java, and frontend verification**

```bash
cd AI-Clothing-Shopping-Assistant-System
./.venv/bin/python -m unittest discover -v
./.venv/bin/python -m compileall -q clothing_assistant tests

cd ../IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -q test

cd ../frontend
npm test -- --run
npm run build
```

## Completion Criteria

- New developers can start MySQL and Redis through one documented root command.
- Java backend defaults match the documented Docker Compose MySQL port.
- Root CI covers shared contract, Python, Java, and frontend checks.
- Stage 4 can focus on AI fallback/resilience instead of local setup uncertainty.
