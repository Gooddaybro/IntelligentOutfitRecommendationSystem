# Middleware Learning and Evolution Roadmap

> **For agentic workers:** Treat this as the current middleware decision and learning roadmap. Do not add a message broker until the MQ decision gate is completed and a separate, focused implementation plan is approved.

**Goal:** Consolidate the implemented Redis capability, turn it into verifiable project knowledge, and prepare a low-risk path for a future MQ introduction.

**Architecture:** Keep the Java modular monolith as the commerce source of truth and the Python service as the AI orchestration layer. Redis remains an acceleration and protection layer. A future broker may carry independent async work, but it must not replace Java transactions for order, inventory, or payment state.

**Tech Stack:** Java 21, Spring Boot 4, MySQL 8, Redis 7.2, Spring Data Redis, React, Python FastAPI/LangGraph. No RabbitMQ, Kafka, RocketMQ, or Redis Streams runtime dependency is currently configured.

## Current Snapshot — 2026-07-11

### Implemented Redis scope

- Redis 7.2 starts from `IntelligentOutfitRecommendationSystem/docker-compose.yml`.
- `spring-boot-starter-data-redis` and Redis connection properties are configured in the Java backend.
- `RedisCacheService` provides JSON get/set/delete operations and a counter that assigns TTL on the first increment.
- Product search/detail, user profile, and recommendation candidates use Cache Aside reads.
- AI chat uses a user-level fixed-window counter; Redis failures fail open so a cache outage does not block the AI entry point.
- Unit tests cover cache hit/miss, profile cache deletion, and rate-limit behavior. The manual verification guide is `docs/redis-cache-rate-limit-manual-test.md`.

### Not implemented

- No production message broker or Spring AMQP/Kafka dependency.
- No asynchronous task table, task-status API, producer confirmation, consumer acknowledgement, retry policy, dead-letter queue, idempotency store, or transactional outbox.
- No Redis lock, Redis Cluster, or Redis-backed transaction/inventory source of truth.

## Documentation Roles

| Document | Role |
|---|---|
| `docs/architecture/2026-06-25-distributed-modernization-interview-design.md` | Architecture reasoning, trade-offs, and interview language |
| `docs/superpowers/plans/2026-06-26-redis-cache-rate-limit-mvp.md` | Completed Redis MVP implementation record |
| `docs/superpowers/plans/2026-07-05-ai-commerce-next-development-roadmap.md` | Completed six-stage product roadmap and its constraints |
| `docs/superpowers/plans/2026-07-05-release-readiness-handoff.md` | Release, sandbox-callback, and commit-cleanup work |
| This document | Current Redis learning checklist and MQ decision gate |

## Architecture Decisions Already Made

1. **Modular monolith first.** Do not introduce Nacos, Gateway, service registry, or microservice splitting for this learning stage.
2. **MySQL is the source of truth.** Redis must not store the only copy of inventory, order, payment, or user ownership state.
3. **Cache Aside is the default cache pattern.** Update MySQL first, then delete the corresponding Redis key.
4. **Synchronous chat stays HTTP/SSE.** A normal interactive chat request is not the first MQ candidate.
5. **MQ is for independent async work.** It can handle long-running AI jobs or post-payment side effects only after their state and retry semantics are explicit.

## Track 1: Redis Learning and Verification

### Learning outcomes

- Explain the Cache Aside read path and the "write MySQL, then delete cache" invalidation path using this project.
- Explain why cache TTL has jitter, and distinguish cache penetration, breakdown, and avalanche.
- Explain why the AI rate limiter is fixed-window and why it fails open when Redis is unavailable.
- State which data must not be made Redis-only: stock, order state, payment state, and transaction records.

### Checklist

- [ ] Run the product-detail cold-cache and warm-cache experiment from `docs/redis-cache-rate-limit-manual-test.md`.
- [ ] Inspect a product-detail key with `GET` and `TTL`, then delete it and repeat the request.
- [ ] Update and reread a user profile to observe cache invalidation and reload.
- [ ] Send more than the configured AI requests in one minute and inspect the rate-limit counter.
- [ ] Read `RedisCacheService`, `ProductCatalogService`, `UserProfileService`, and `AssistantRateLimitService` in that order; write a one-paragraph explanation for each boundary.
- [ ] Run the focused Redis unit tests before claiming the Redis path is understood.

### Focused verification command

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -Dtest=ProductCatalogServiceTests,UserProfileServiceTests,AssistantRateLimitServiceTests test
```

## Track 2: MQ Decision Gate

Do not add a broker, dependency, Docker service, or Java/Python code while this section remains undecided.

### Proposed first scenario

**Recommended:** `ai.task.requested` for an explicitly long-running operation such as RAG rebuilding, batch product-tag generation, or a recommendation evaluation report.

This is preferred over payment events as a first learning slice because a failed AI batch task does not change payment, order, or inventory truth. It also keeps normal `/chat` and `/chat/stream` synchronous.

### Decisions required before implementation

- [ ] Confirm the first event name and one user-visible task outcome.
- [ ] Confirm RabbitMQ as the first broker. Kafka, RocketMQ, and Redis Streams remain comparison topics, not project dependencies.
- [ ] Define the task state transitions: `PENDING -> PROCESSING -> SUCCESS | FAILED`.
- [ ] Define the JSON event envelope: `eventId`, `eventType`, `taskId`, `userId`, `occurredAt`, and `schemaVersion`.
- [ ] Decide whether Java or Python consumes the first version; begin with one consumer boundary only.
- [ ] Define duplicate-message handling using `eventId` before writing a consumer.
- [ ] Define retry limit, terminal failure behavior, and dead-letter handling before enabling retries.
- [ ] Decide whether an outbox is required after the basic producer/consumer path is proven; do not claim exactly-once delivery.

### Explicitly out of scope

- Replacing order creation, stock lock/confirm/release, or payment-success confirmation with MQ.
- Replacing the existing chat SSE path with MQ.
- Introducing microservice infrastructure only to demonstrate a broker.
- Using Redis as a substitute for RabbitMQ in the production architecture.

## Next Planning Boundary

After every decision in **Track 2** is accepted, create a separate plan named `YYYY-MM-DD-rabbitmq-ai-task-mvp.md`. That implementation plan must cover the exact files, Docker setup, task persistence, message contract, producer confirmation, consumer acknowledgement, retry/DLQ behavior, idempotency tests, and manual verification steps.

## Interview Summary

> This project first uses Redis for high-frequency reads and AI request protection while MySQL remains the source of truth. MQ is deliberately deferred until there is a real independent async workload. The first candidate is a long-running AI task, not an order or payment transaction, so the project can demonstrate event-driven decoupling without weakening transaction correctness.
