# RabbitMQ RAG 全局索引重建 Runbook

## 适用范围

本手册只处理管理员 `RAG_REBUILD` 链路。聊天、订单、库存和支付不经过 RabbitMQ；Broker
故障时这些同步链路应继续可用。MySQL `ai_task` 是任务事实源，RabbitMQ 只安排执行。

## 快速检查

1. 查看 `GET /api/ai/tasks/{taskId}` 的状态、尝试次数和安全错误摘要。
2. 查看 Grafana 的 AI Task、Outbox、Retry、DLQ 面板。
3. 查看 RabbitMQ Management 中主队列、三个 Retry 队列和 DLQ。
4. 查看 Python `GET /health/rag` 的当前索引版本；不要把未校验的版本目录手工设为当前版本。

## Broker 不可用或 Outbox 持续增长

1. 将 `APP_AI_TASK_PUBLISHER_ENABLED=false`，停止 Relay 新发布尝试。
2. 确认聊天和交易 API 仍正常；不要通过关闭整个 Java Web 进程回滚。
3. 检查 RabbitMQ 容器、磁盘、连接数和凭据，然后恢复 Broker。
4. 恢复后先观察队列和 Confirm，再开启 Publisher。Outbox `NEW` 事件会被重新 claim。

## Python 内部鉴权失败

`401/403` 属于永久失败，会直接进入 DLQ。核对 Java Worker 的
`APP_AI_PYTHON_INTERNAL_TOKEN` 与 Python 的 `APP_INTERNAL_API_TOKEN`，不得在日志、消息或
工单中粘贴令牌。修复后由管理员调用 Redrive，禁止手工修改原始消息。

## Retry 风暴

1. 关闭 Publisher，保留已经入队的消息和 MySQL 事实。
2. 判断 Python 是否持续返回 429/5xx 或超时。
3. 生产延迟固定为 10 秒、60 秒、300 秒；不要接受消息自带的任意延迟。
4. 修复依赖后逐步恢复 Worker，观察主队列和 Retry 队列下降速度。

## DLQ 与 Redrive

1. 先读取任务的 `failureCode/failureSummary`，确认根因已经解除。
2. 仅管理员可调用 `POST /api/ai/tasks/{taskId}/redrive`。
3. Redrive 必须产生新 `eventId` 和审计记录，但保持原 `taskId`，以便 Python 幂等返回。
4. 不从 Management UI 任意编辑并重投消息；这会绕过 Java 审计和活动槽唯一约束。

## 安全回滚

首选回滚开关是 `APP_AI_TASK_PUBLISHER_ENABLED=false`。它只停止 Outbox 发布，不删除任务、
消息或索引。重建失败时 Python 继续使用旧索引；不要把 `current.json` 指向未完整校验的目录。
