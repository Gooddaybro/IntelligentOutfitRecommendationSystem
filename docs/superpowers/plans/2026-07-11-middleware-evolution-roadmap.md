# Middleware Learning and Evolution Roadmap

> **For agentic workers:** Track 2 decisions were approved on 2026-07-15. The focused design is `docs/superpowers/specs/2026-07-15-rabbitmq-rag-rebuild-mvp-design.md`; do not expand its implementation scope to the deferred scenarios below.

**Goal:** Consolidate the implemented Redis capability, turn it into verifiable project knowledge, and prepare a low-risk path for a future MQ introduction.

**Architecture:** Keep the Java modular monolith as the commerce source of truth and the Python service as the AI orchestration layer. Redis remains an acceleration and protection layer. A future broker may carry independent async work, but it must not replace Java transactions for order, inventory, or payment state.

**Tech Stack:** Java 21, Spring Boot 4, MySQL 8, Redis 7.2, Spring Data Redis, React, Python FastAPI/LangGraph. RabbitMQ is approved for the fourth-week `RAG_REBUILD` vertical slice but is not yet implemented.

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

The decision gate is complete. Implementation still requires a separate approved plan.

### Proposed first scenario

**Recommended:** `ai.task.requested` for an explicitly long-running operation such as RAG rebuilding, batch product-tag generation, or a recommendation evaluation report.

This is preferred over payment events as a first learning slice because a failed AI batch task does not change payment, order, or inventory truth. It also keeps normal `/chat` and `/chat/stream` synchronous.

### Decisions required before implementation

- [x] Use `ai.task.requested` schema v1 for an administrator-visible `RAG_REBUILD` task.
- [x] Use RabbitMQ as the first broker. Kafka, RocketMQ, and Redis Streams remain comparison topics, not project dependencies.
- [x] Use `PENDING -> PROCESSING -> SUCCESS`, `PROCESSING -> RETRY_WAIT -> PROCESSING`, and terminal `FAILED` transitions.
- [x] Use a small JSON envelope containing `eventId`, `eventType`, `schemaVersion`, `taskId`, `taskType`, `occurredAt`, `correlationId`, and `traceparent`.
- [x] Let a Java Worker consume RabbitMQ and call a protected Python internal rebuild endpoint.
- [x] Handle duplicate delivery with API coalescing, `eventId` Inbox uniqueness, task state/lease, and Python `taskId` idempotency.
- [x] Use bounded 10-second, 60-second, and 300-second retries, followed by a final DLQ and administrator Redrive.
- [x] Require Transactional Outbox, Publisher Confirm, manual ACK, and MySQL idempotency; do not claim exactly-once delivery.

### Deferred MQ development backlog

The following scenarios may reuse the reliable task framework after the `RAG_REBUILD` MVP. They are recorded only; they are not part of the fourth-week implementation:

1. Recommendation evaluation reports.
2. Batch AI-generated product tags.
3. Product-image compression, thumbnails, and visual attribute extraction.
4. User-profile, popularity, and recommendation-feature computation from persisted behavior events.
5. Non-core post-payment side effects such as notifications, ordinary points, reports, and logistics notifications.
6. Email, SMS, and in-app notification delivery.

Each scenario requires its own fact source, idempotency key, retry taxonomy, failure boundary, design, and implementation plan.

### Explicitly out of scope

- Replacing order creation, stock lock/confirm/release, or payment-success confirmation with MQ.
- Replacing the existing chat SSE path with MQ.
- Introducing microservice infrastructure only to demonstrate a broker.
- Using Redis as a substitute for RabbitMQ in the production architecture.

## Next Planning Boundary

After the focused design is reviewed, create `docs/superpowers/plans/2026-07-15-rabbitmq-rag-rebuild-mvp.md`. The implementation plan must cover exact files in both repositories, Docker setup, task persistence, message contract, Publisher Confirm, manual ACK, retry/DLQ, Redrive, Python index safety, idempotency tests, and real-process verification.

## Interview Summary

> This project first uses Redis for high-frequency reads and AI request protection while MySQL remains the source of truth. MQ is deliberately deferred until there is a real independent async workload. The first candidate is a long-running AI task, not an order or payment transaction, so the project can demonstrate event-driven decoupling without weakening transaction correctness.
