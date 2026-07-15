# RabbitMQ RAG Rebuild MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付管理员专属的全局 `RAG_REBUILD` 真实异步闭环，并通过 Transactional Outbox、Publisher Confirm、手动 ACK、Inbox/lease 幂等、有限 Retry、DLQ 和 Redrive 证明 RabbitMQ 可靠性。

**Architecture:** Java Web 负责管理员鉴权及 `ai_task + outbox_event` 同事务创建；Java Relay 把 Outbox 可靠发布到 RabbitMQ；独立 Java Worker 消费后调用 Python 内部重建 API，Python 使用 `taskId` 幂等并在校验成功后切换索引。MySQL 保存任务事实，RabbitMQ 只安排执行，普通聊天和交易链路不依赖 MQ。

**Tech Stack:** Java 21、Spring Boot 4.0.6、Spring AMQP 4、MyBatis、Flyway、MySQL 8、RabbitMQ 4 Management、Micrometer、Testcontainers 1.21.4、Python 3.13、FastAPI、Pydantic、unittest

---

## Execution boundaries

- Java repository: `D:\git\推荐系统\Intelligent Outfit Recommendation System`, branch `learn`.
- Python repository: `D:\git\推荐系统\AI Clothing Shopping Assistant System`, branch `tree`.
- Shared local contract: `D:\git\推荐系统\outfit-project-contract`.
- The Python worktree already contains unrelated `.idea`, learning-demo, and documentation changes. Do not reset, stash, switch branches, or include those files in RabbitMQ commits.
- Read `D:\git\推荐系统\Intelligent Outfit Recommendation System\docs\commenting-guidelines.md` before Java source or Mapper changes. Every new production Java top-level type needs boundary-focused Javadoc.
- Do not put RabbitMQ code in `common`; keep it inside the new `aitask` deep module so ArchUnit's cross-module Mapper rule remains valid.
- Use `spring.rabbitmq.publisher-confirm-type=correlated`, publisher returns, `CorrelationData.getFuture()`, and `Confirm.ack()`; Spring AMQP 4 deprecates the old `isAck()` accessor.

## Target file map

### Shared contract

- Create `D:\git\推荐系统\outfit-project-contract\contracts\rag-rebuild\v1.md`.
- Create `D:\git\推荐系统\outfit-project-contract\contracts\rag-rebuild\schemas\rag-rebuild-request.schema.json`.
- Create `D:\git\推荐系统\outfit-project-contract\contracts\rag-rebuild\schemas\rag-rebuild-response.schema.json`.
- Create `D:\git\推荐系统\outfit-project-contract\contracts\rag-rebuild\schemas\ai-task-requested.schema.json`.

### Python

- Modify `clothing_assistant/infrastructure/vector_store.py`: version metadata, `taskId` idempotency, staged build and safe switch.
- Modify `clothing_assistant/api/schemas.py`: internal rebuild request/response schemas.
- Modify `clothing_assistant/api/app.py`: protected `/internal/rag/rebuild` endpoint.
- Modify `tests/test_vector_store.py`: safe-switch and idempotency tests.
- Modify `tests/test_api.py`: internal auth and response-contract tests.
- Create `tests/test_rag_rebuild_contract.py`: shared JSON-schema field checks.

### Java

- Modify `backend/pom.xml`: Spring AMQP and RabbitMQ Testcontainers dependencies.
- Modify `docker-compose.yml`: RabbitMQ management service and persistent volume.
- Modify `backend/src/main/resources/application.properties` and test properties: broker, profiles, confirms, manual ACK, feature flags and timeouts.
- Create `backend/src/main/resources/db/migration/V19__ai_task_outbox_inbox.sql`.
- Create package `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/**` for API, DTO, model, Mapper, service, client and messaging components.
- Create `backend/src/main/resources/mapper/aitask/*.xml`.
- Modify `security/SecurityConfig.java`: protect `/api/ai/tasks/**` with `ROLE_ADMIN`.
- Modify `common/observability/ApplicationMetrics.java`: fixed-cardinality AI task metrics.
- Modify `observability/prometheus.yml` and add RabbitMQ/RAG panels to `observability/grafana/dashboards/ai-shopping-assistant.json`.
- Create `observability/rabbitmq/enabled_plugins` so the broker exposes official Prometheus metrics on port 15692.
- Create `docs/runbooks/rabbitmq-rag-rebuild.md` and update the 2026-07-14 architecture status document after verification.

---

### Task 1: Freeze the cross-project contract

**Files:**
- Create: `D:\git\推荐系统\outfit-project-contract\contracts\rag-rebuild\v1.md`
- Create: `D:\git\推荐系统\outfit-project-contract\contracts\rag-rebuild\schemas\rag-rebuild-request.schema.json`
- Create: `D:\git\推荐系统\outfit-project-contract\contracts\rag-rebuild\schemas\rag-rebuild-response.schema.json`
- Create: `D:\git\推荐系统\outfit-project-contract\contracts\rag-rebuild\schemas\ai-task-requested.schema.json`
- Create: `D:\git\推荐系统\AI Clothing Shopping Assistant System\tests\test_rag_rebuild_contract.py`

- [ ] **Step 1: Write the failing Python contract test**

```python
class RagRebuildContractTests(unittest.TestCase):
    def test_request_and_response_fields_match_shared_contract(self):
        request_schema = load_schema("rag-rebuild-request.schema.json")
        response_schema = load_schema("rag-rebuild-response.schema.json")
        self.assertEqual(set(request_schema["required"]), {"taskId", "source"})
        self.assertEqual(
            set(response_schema["required"]),
            {"taskId", "indexVersion", "fileCount", "chunkCount", "contentDigest", "replayed"},
        )
```

- [ ] **Step 2: Run it and confirm the missing contract fails**

Run from the Python repository:

```powershell
python -m unittest tests.test_rag_rebuild_contract -v
```

Expected: FAIL because `contracts/rag-rebuild/schemas` does not exist.

- [ ] **Step 3: Write the contract schemas and prose contract**

Use this event envelope as the exact v1 required set:

```json
{
  "eventId": "uuid",
  "eventType": "ai.task.requested",
  "schemaVersion": 1,
  "taskId": "task_01...",
  "taskType": "RAG_REBUILD",
  "occurredAt": "2026-07-15T17:00:00+08:00",
  "correlationId": "request-id",
  "traceparent": "00-trace-span-01"
}
```

`v1.md` must state that Java owns authorization and task state, Python owns index building, `source` is fixed to `LOCAL_GLOBAL_KNOWLEDGE`, and the endpoint is idempotent by `taskId`.

- [ ] **Step 4: Run the focused contract test**

```powershell
python -m unittest tests.test_rag_rebuild_contract -v
```

Expected: PASS.

- [ ] **Step 5: Commit only the Python contract test**

The shared contract directory is not a Git repository; keep those files in place and commit only the new Python test:

```powershell
git add tests/test_rag_rebuild_contract.py
git commit -m "test: 固化 RAG 重建内部契约"
```

---

### Task 2: Make Python index rebuild task-idempotent and failure-safe

**Files:**
- Modify: `D:\git\推荐系统\AI Clothing Shopping Assistant System\clothing_assistant\infrastructure\vector_store.py`
- Modify: `D:\git\推荐系统\AI Clothing Shopping Assistant System\tests\test_vector_store.py`

- [ ] **Step 1: Add failing safe-switch tests**

Add tests proving:

```python
def test_rebuild_records_source_task_id_and_replays_same_task(self):
    first = vector_store.rebuild_vector_store_from_local_knowledge(task_id="task-1")
    second = vector_store.rebuild_vector_store_from_local_knowledge(task_id="task-1")
    self.assertFalse(first["replayed"])
    self.assertTrue(second["replayed"])
    self.assertEqual(first["index_version"], second["index_version"])

def test_failed_staged_build_keeps_current_store(self):
    old_pointer = json.loads(vector_store.VECTOR_STORE_POINTER_FILE.read_text(encoding="utf-8"))
    with patch.object(vector_store, "write_json_atomically", side_effect=OSError("disk full")):
        with self.assertRaises(OSError):
            vector_store.rebuild_vector_store_from_local_knowledge(task_id="task-2")
    self.assertEqual(
        json.loads(vector_store.VECTOR_STORE_POINTER_FILE.read_text(encoding="utf-8")),
        old_pointer,
    )
```

- [ ] **Step 2: Run focused tests and confirm failure**

```powershell
python -m unittest tests.test_vector_store -v
```

Expected: FAIL because the rebuild function has no `task_id` contract and returns the legacy tuple.

- [ ] **Step 3: Implement the smallest safe rebuild result**

Introduce a result dictionary with exact keys:

```python
{
    "task_id": task_id,
    "index_version": meta["version"],
    "file_count": len(meta["source_files"]),
    "chunk_count": meta["chunk_count"],
    "content_digest": meta["content_digest"],
    "replayed": replayed,
}
```

Build vectors and metadata under `VECTOR_DB_DIR/versions/<index-version>/`, add `source_task_id` and a deterministic SHA-256 `content_digest` to metadata, validate both version files, then atomically replace only `VECTOR_STORE_POINTER_FILE` (`current.json`). All reads resolve the current version through that pointer, with a temporary legacy fallback for the existing fixed files. Delete an incomplete version directory on failure and update `_VECTOR_DATA_CACHE` only after the pointer switch. If current metadata contains the same `source_task_id` and the store validates, return it with `replayed=True` without calling embeddings.

- [ ] **Step 4: Run vector-store tests**

```powershell
python -m unittest tests.test_vector_store -v
```

Expected: PASS.

- [ ] **Step 5: Commit the Python index safety change**

```powershell
git add clothing_assistant/infrastructure/vector_store.py tests/test_vector_store.py
git commit -m "feat: 支持幂等安全的 RAG 索引重建"
```

---

### Task 3: Expose the protected Python rebuild endpoint

**Files:**
- Modify: `D:\git\推荐系统\AI Clothing Shopping Assistant System\clothing_assistant\api\schemas.py`
- Modify: `D:\git\推荐系统\AI Clothing Shopping Assistant System\clothing_assistant\api\app.py`
- Modify: `D:\git\推荐系统\AI Clothing Shopping Assistant System\tests\test_api.py`

- [ ] **Step 1: Add failing endpoint tests**

Cover missing token `401`, valid token `200`, invalid source `422`, same task replay, and sanitized `500`:

```python
response = client.post(
    "/internal/rag/rebuild",
    headers={"X-Internal-Token": "test-internal-token"},
    json={"taskId": "task-1", "source": "LOCAL_GLOBAL_KNOWLEDGE"},
)
self.assertEqual(response.status_code, 200)
self.assertEqual(response.json()["taskId"], "task-1")
```

- [ ] **Step 2: Run endpoint tests and confirm 404**

```powershell
python -m unittest tests.test_api -v
```

Expected: FAIL because `/internal/rag/rebuild` is absent.

- [ ] **Step 3: Add exact Pydantic schemas and route**

```python
class RagRebuildRequest(BaseModel):
    task_id: str = Field(alias="taskId", min_length=1, max_length=64)
    source: Literal["LOCAL_GLOBAL_KNOWLEDGE"]

class RagRebuildResponse(BaseModel):
    task_id: str = Field(alias="taskId")
    index_version: str = Field(alias="indexVersion")
    file_count: int = Field(alias="fileCount", ge=0)
    chunk_count: int = Field(alias="chunkCount", ge=0)
    content_digest: str = Field(alias="contentDigest")
    replayed: bool
```

The route must use `Depends(require_internal_auth)` and `asyncio.to_thread(...)` so the synchronous embedding build does not block the FastAPI event loop.

- [ ] **Step 4: Run Python focused and quality checks**

```powershell
python -m unittest tests.test_api tests.test_vector_store tests.test_rag_rebuild_contract -v
ruff check clothing_assistant tests
```

Expected: PASS with no Ruff violations.

- [ ] **Step 5: Commit the Python API change without unrelated dirty files**

```powershell
git add clothing_assistant/api/app.py clothing_assistant/api/schemas.py tests/test_api.py
git commit -m "feat: 提供内部 RAG 索引重建接口"
```

---

### Task 4: Add Java task, Outbox, Inbox and Redrive persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__ai_task_outbox_inbox.sql`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/model/AiTask.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/model/OutboxEvent.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/service/AiTaskStatus.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/service/AiTaskType.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/mapper/AiTaskMapper.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/mapper/OutboxEventMapper.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/mapper/ConsumerInboxMapper.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/mapper/AiTaskRedriveAuditMapper.java`
- Create: `backend/src/main/resources/mapper/aitask/*.xml`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/AiTaskPersistenceTests.java`

- [ ] **Step 1: Add failing V19 and persistence tests**

Test that V19 creates all four tables and that two concurrent inserts using `active_slot='GLOBAL_RAG_REBUILD'` cannot both succeed.

- [ ] **Step 2: Run the focused persistence tests**

```powershell
cd backend
$env:RUN_MYSQL_TESTS='true'
.\mvnw.cmd -Dtest=MySqlFlywayMigrationTests,AiTaskPersistenceTests test
```

Expected: FAIL because V19 and Mapper types are missing.

- [ ] **Step 3: Create V19 with database-enforced active-task uniqueness**

The `ai_task` table must include:

```sql
task_id VARCHAR(64) NOT NULL UNIQUE,
task_type VARCHAR(32) NOT NULL,
created_by BIGINT NOT NULL,
status VARCHAR(24) NOT NULL,
active_slot VARCHAR(64) NULL,
attempt_count INT NOT NULL DEFAULT 0,
worker_id VARCHAR(64) NULL,
lease_until DATETIME(6) NULL,
version BIGINT NOT NULL DEFAULT 0,
failure_code VARCHAR(64) NULL,
failure_summary VARCHAR(500) NULL,
UNIQUE KEY uk_ai_task_active_slot (active_slot)
```

Also create `outbox_event`, `consumer_inbox` with `PRIMARY KEY (consumer_name,event_id)`, and `ai_task_redrive_audit`. Use JSON-compatible `LONGTEXT` for H2/MySQL portability and validate serialized payload in Java.

- [ ] **Step 4: Implement Mapper operations**

Required atomic operations include `insertTask`, `findActiveTask`, `claimTask(expectedVersion, now, leaseUntil)`, `markRetryWait`, `markSuccessAndClearActiveSlot`, `markFailedAndClearActiveSlot`, `insertOutbox`, `claimOutbox`, `markPublished`, `insertInbox`, and `insertRedriveAudit`.

- [ ] **Step 5: Run persistence tests**

```powershell
.\mvnw.cmd -Dtest=MySqlFlywayMigrationTests,AiTaskPersistenceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit Java persistence**

```powershell
git add backend/src/main/resources/db/migration/V19__ai_task_outbox_inbox.sql backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask backend/src/main/resources/mapper/aitask backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java
git commit -m "feat: 建立 AI 任务与消息可靠性表"
```

---

### Task 5: Implement the administrator task API and coalescing

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/api/AiTaskController.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/dto/CreateAiTaskRequest.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/dto/AiTaskResponse.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/dto/RedriveAiTaskRequest.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/service/AiTaskApplicationService.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/service/AiTaskEventFactory.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/SecurityConfig.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/AiTaskControllerTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/AiTaskApplicationServiceTests.java`

- [ ] **Step 1: Write failing authorization, transaction and coalescing tests**

Use Spring Security test JWT authorities:

```java
mockMvc.perform(post("/api/ai/tasks")
        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"taskType\":\"RAG_REBUILD\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.replayed").value(false));
```

Also prove `ROLE_USER -> 403`, concurrent create returns the same active task, and Redrive only accepts `FAILED` tasks while inserting a new `eventId` plus audit in one transaction.

- [ ] **Step 2: Run tests and confirm failure**

```powershell
.\mvnw.cmd -Dtest=AiTaskControllerTests,AiTaskApplicationServiceTests test
```

- [ ] **Step 3: Implement the API**

Add this security rule before `.anyRequest()`:

```java
.requestMatchers("/api/ai/tasks/**").hasRole("ADMIN")
```

`POST /api/ai/tasks` returns HTTP 202. The service first reads an active task, then attempts insert; on duplicate-key it re-reads the active task. Task and Outbox insertion are inside one `@Transactional` method. `GET /api/ai/tasks/{taskId}` and `POST /api/ai/tasks/{taskId}/redrive` are also administrator-only.

- [ ] **Step 4: Run API and service tests**

```powershell
.\mvnw.cmd -Dtest=AiTaskControllerTests,AiTaskApplicationServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit the API slice**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/SecurityConfig.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask
git commit -m "feat: 提供管理员 AI 重建任务 API"
```

---

### Task 6: Add RabbitMQ topology and local infrastructure

**Files:**
- Modify: `backend/pom.xml`
- Modify: `docker-compose.yml`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/test/resources/application-test.properties`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/messaging/AiTaskMessagingProperties.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/messaging/RabbitAiTaskTopology.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/RabbitAiTaskTopologyTests.java`

- [ ] **Step 1: Write the failing topology test**

Use `RabbitMQContainer("rabbitmq:4.1.8-management")` and assert main, three retry and DLQ queues exist after the Spring context starts.

- [ ] **Step 2: Add dependencies and run the failing test**

Add `spring-boot-starter-amqp` and test-scope `org.testcontainers:rabbitmq:${testcontainers.version}`. Run:

```powershell
.\mvnw.cmd -Dtest=RabbitAiTaskTopologyTests test
```

- [ ] **Step 3: Declare topology and properties**

Configure correlated confirms, returns, mandatory publishing and manual ACK. Set `spring.profiles.default=web`; put the Controller and Relay behind `@Profile("web")`, and the listener behind `@Profile("worker")`. In test properties disable publisher scheduling and listener auto-start unless an integration test enables them. Declare the exact names from the design. Each retry queue uses its own configurable TTL and dead-letters back to the main routing key; integration tests override 10s/60s/300s with 100ms/200ms/300ms so the suite stays fast.

- [ ] **Step 4: Add Docker Compose RabbitMQ**

```yaml
rabbitmq:
  image: rabbitmq:4.1.8-management
  container_name: intelligent_outfit_rabbitmq
  ports:
    - "${RABBITMQ_AMQP_HOST_PORT:-5672}:5672"
    - "${RABBITMQ_MANAGEMENT_HOST_PORT:-15672}:15672"
    - "${RABBITMQ_PROMETHEUS_HOST_PORT:-15692}:15692"
  environment:
    RABBITMQ_DEFAULT_USER: ${RABBITMQ_USERNAME:-app}
    RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-app-dev-password}
  volumes:
    - ./observability/rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro
    - intelligent_outfit_rabbitmq_data:/var/lib/rabbitmq
```

- [ ] **Step 5: Run topology test and static Compose validation**

```powershell
.\mvnw.cmd -Dtest=RabbitAiTaskTopologyTests test
docker compose config --quiet
```

Expected: both commands exit 0.

- [ ] **Step 6: Commit broker topology**

```powershell
git add backend/pom.xml docker-compose.yml backend/src/main/resources/application.properties backend/src/test/resources/application-test.properties backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/messaging backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/RabbitAiTaskTopologyTests.java
git commit -m "feat: 配置 RabbitMQ AI 任务拓扑"
```

---

### Task 7: Publish Outbox events only after correlated confirms

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/messaging/AiTaskOutboxRelay.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/messaging/AiTaskMessage.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/AiTaskOutboxRelayTests.java`

- [ ] **Step 1: Write failing confirm/nack/return tests**

Mock `RabbitTemplate` and `CorrelationData` future. Prove ACK marks `PUBLISHED`; nack, return, timeout and broker exception leave the event recoverable and never mark it published.

- [ ] **Step 2: Run focused tests and confirm failure**

```powershell
.\mvnw.cmd -Dtest=AiTaskOutboxRelayTests test
```

- [ ] **Step 3: Implement an enabled, bounded relay**

The `web`-profile scheduler must be guarded by `app.ai-task.publisher-enabled`, claim at most the configured batch size, publish with `CorrelationData(eventId)`, and use:

```java
CorrelationData.Confirm confirm = correlationData.getFuture()
        .get(properties.getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
if (!confirm.ack() || correlationData.getReturned() != null) {
    throw new IllegalStateException("rabbit publish was not confirmed");
}
```

Only after this block succeeds may it mark the Outbox event `PUBLISHED`.

- [ ] **Step 4: Run relay tests**

```powershell
.\mvnw.cmd -Dtest=AiTaskOutboxRelayTests test
```

- [ ] **Step 5: Commit the relay**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/messaging backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/AiTaskOutboxRelayTests.java
git commit -m "feat: 使用发布确认投递 Outbox 事件"
```

---

### Task 8: Consume with lease, Python task idempotency and manual ACK

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/client/RagRebuildClient.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/client/HttpRagRebuildClient.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/client/RagRebuildResult.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/messaging/AiTaskWorker.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/service/AiTaskExecutionService.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/HttpRagRebuildClientTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/AiTaskExecutionServiceTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/AiTaskWorkerTests.java`

- [ ] **Step 1: Write failing propagation and idempotency tests**

Prove the client sends `X-Internal-Token`, `X-Request-Id`, and `traceparent`; prove task already `SUCCESS` or Inbox hit skips Python and ACKs; prove Python success followed by database success writes Inbox and then ACKs; prove database failure never ACKs.

- [ ] **Step 2: Run focused tests and confirm failure**

```powershell
.\mvnw.cmd -Dtest=HttpRagRebuildClientTests,AiTaskExecutionServiceTests,AiTaskWorkerTests test
```

- [ ] **Step 3: Implement the long-task client**

Use a dedicated `app.ai-task.python-read-timeout` rather than chat timeout. Map `401/403` to permanent authentication failure, `429` and `5xx` to retryable failure, and validate that response `taskId` equals the requested task.

- [ ] **Step 4: Implement claim/lease and terminal transaction**

The execution sequence is fixed:

```text
parse and validate message
-> duplicate/terminal check
-> conditional task claim with lease
-> call Python outside the database transaction
-> one transaction: mark SUCCESS + clear active_slot + insert Inbox
-> manual basicAck
```

If the process dies after Python succeeds but before Java commit, redelivery calls Python with the same `taskId`; Python returns `replayed=true`, then Java completes its transaction.

- [ ] **Step 5: Run worker tests**

```powershell
.\mvnw.cmd -Dtest=HttpRagRebuildClientTests,AiTaskExecutionServiceTests,AiTaskWorkerTests test
```

- [ ] **Step 6: Commit worker and client**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask
git commit -m "feat: 实现幂等 RAG 重建 Worker"
```

---

### Task 9: Add bounded Retry, final DLQ and audited Redrive

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/messaging/AiTaskWorker.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/messaging/AiTaskFailureClassifier.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/service/AiTaskApplicationService.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/AiTaskRetryDlqIntegrationTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/AiTaskApplicationServiceTests.java`

- [ ] **Step 1: Write failing retry and DLQ tests**

With real RabbitMQ, prove timeout/429/5xx go through 10s, 60s and 300s stages; invalid schema and auth failures go straight to final DLQ; retry exhaustion marks the task `FAILED`; Redrive creates a new event ID and audit row.

- [ ] **Step 2: Run the integration test and confirm failure**

```powershell
.\mvnw.cmd -Dtest=AiTaskRetryDlqIntegrationTests test
```

- [ ] **Step 3: Implement failure classification and routing**

Use a bounded integer `x-retry-stage` header. Never trust arbitrary delay values from a message. Before publishing to retry, transactionally increment attempt count and set `RETRY_WAIT`; before final DLQ, set `FAILED`, clear `active_slot`, and store a safe failure code/summary. Production delays remain 10s/60s/300s; integration tests inject 100ms/200ms/300ms.

- [ ] **Step 4: Implement Redrive transaction**

For `FAILED` only: set `PENDING`, restore `active_slot`, clear failure/lease fields, create a fresh `eventId` Outbox event, and insert `ai_task_redrive_audit` in the same transaction. A concurrent new task or Redrive collision must return the existing active task rather than create two global rebuilds.

- [ ] **Step 5: Run retry/DLQ and service tests**

```powershell
.\mvnw.cmd -Dtest=AiTaskRetryDlqIntegrationTests,AiTaskApplicationServiceTests test
```

- [ ] **Step 6: Commit reliability behavior**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask
git commit -m "feat: 增加 AI 任务重试 DLQ 与重放"
```

---

### Task 10: Add low-cardinality metrics, dashboard and Runbook

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetrics.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetricsTests.java`
- Modify: `observability/prometheus.yml`
- Modify: `observability/grafana/dashboards/ai-shopping-assistant.json`
- Create: `observability/rabbitmq/enabled_plugins`
- Create: `docs/runbooks/rabbitmq-rag-rebuild.md`

- [ ] **Step 1: Write failing metric tests**

Test fixed labels only: `taskType=rag_rebuild`, task outcome, publish outcome, consume outcome and retry stage. Unknown values must collapse to `other`; task ID, event ID, user ID and request ID must never be tags.

- [ ] **Step 2: Run metric tests and confirm failure**

```powershell
.\mvnw.cmd -Dtest=ApplicationMetricsTests test
```

- [ ] **Step 3: Add metrics at successful state boundaries**

Record creation after transaction commit, publish after confirm, success after task/Inbox commit, retry after `RETRY_WAIT`, and failure after final state commit. Add gauges for pending Outbox, active tasks and DLQ depth through bounded infrastructure collectors.

- [ ] **Step 4: Add dashboard panels and Runbook**

Write `[rabbitmq_management,rabbitmq_prometheus].` to `enabled_plugins`, add a Prometheus scrape target for `rabbitmq:15692`, and expose the port only for local inspection. The dashboard must show task rate/duration, Outbox backlog/age, worker outcomes, retry count and DLQ depth. The Runbook must cover broker down, Outbox growing, Python auth failure, retry storm, DLQ Redrive and the Publisher Feature Flag rollback.

- [ ] **Step 5: Validate tests and artifacts**

```powershell
.\mvnw.cmd -Dtest=ApplicationMetricsTests test
@'
import json, pathlib, yaml
json.loads(pathlib.Path('observability/grafana/dashboards/ai-shopping-assistant.json').read_text(encoding='utf-8'))
yaml.safe_load(pathlib.Path('observability/prometheus.yml').read_text(encoding='utf-8'))
print('observability artifacts valid')
'@ | python -
```

- [ ] **Step 6: Commit observability**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetrics.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetricsTests.java observability docs/runbooks/rabbitmq-rag-rebuild.md
git commit -m "feat: 监控 RabbitMQ RAG 重建任务"
```

---

### Task 11: Run the real closed loop and close fourth-week documentation

**Files:**
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aitask/RagRebuildEndToEndTests.java`
- Modify: `docs/superpowers/specs/2026-07-14-java-engineering-architecture-polish-design.md`
- Modify: `docs/superpowers/plans/2026-07-15-rabbitmq-rag-rebuild-mvp.md`
- Modify: `.github/workflows/backend-ci.yml` if the existing workflow can run Docker services without weakening other gates.

- [ ] **Step 1: Add an opt-in real-process E2E test**

Gate it with `RUN_RABBITMQ_E2E=true`. Start MySQL and RabbitMQ containers, run Java Web/Worker against a real Python process with a deterministic fake embedding adapter, create an admin task, poll it to `SUCCESS`, and query `/health/rag` to verify the new `taskId`/index version. Also verify `ROLE_USER -> 403` and chat/order endpoints still work with Publisher disabled.

- [ ] **Step 2: Run all focused Java and Python tests**

```powershell
# Python repository
python -m unittest tests.test_rag_rebuild_contract tests.test_vector_store tests.test_api -v
ruff check clothing_assistant tests
interrogate -v -i --fail-under=30 clothing_assistant

# Java repository/backend
$env:RUN_MYSQL_TESTS='true'
$env:RUN_RABBITMQ_E2E='true'
.\mvnw.cmd verify
```

Expected: all tests pass, Checkstyle reports 0 violations, and environment-gated RabbitMQ/MySQL tests execute rather than skip.

- [ ] **Step 3: Run manual infrastructure verification**

```powershell
docker compose --profile observability up -d mysql redis rabbitmq prometheus grafana
docker compose ps
```

Start Python with the internal token and Java Web/Worker profiles, create one admin rebuild task, observe `PENDING -> PROCESSING -> SUCCESS`, confirm both dashboards provision, then stop RabbitMQ and prove chat/order remain available while a new Outbox stays `NEW`.

- [ ] **Step 4: Update status documentation with evidence only**

Record exact test totals, image versions, task ID, index version, retry/DLQ checks and known production calibration limits. Mark every completed checkbox in this plan only after its command has passed.

- [ ] **Step 5: Run final repository checks**

```powershell
git diff --check
git status --short
git log --oneline -12
```

Confirm no `.env`, Secret, Python unrelated dirty file, vector database artifact, `target`, `__pycache__`, or `.superpowers` file is staged.

- [ ] **Step 6: Commit Java fourth-week closure**

```powershell
git add backend docker-compose.yml observability docs .github/workflows/backend-ci.yml
git commit -m "feat: 完成 RabbitMQ 全局索引重建闭环"
```

Do not push either repository until both repository statuses and commit contents have been reviewed.
