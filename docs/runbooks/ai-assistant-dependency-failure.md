# AI 导购依赖故障 Runbook

## 1. 告警含义

满足任一条件时进入本 Runbook：

- `app_ai_circuit_state > 0` 持续超过一个熔断等待周期；
- `app_ai_requests_total{outcome="error"}` 在 5 分钟窗口明显升高；
- `app_ai_fallbacks_total` 持续增长；
- 同步或 SSE 的 `app_ai_request_duration_seconds` 延迟显著高于基线。

这表示 Java 到 Python AI 的调用正在变慢、失败或被熔断旁路。商品、库存、订单和支付事实仍由 Java/MySQL 控制，AI 故障不应改变交易事实，也不应让商城 readiness 失败。

## 2. 第一检查项

1. 查看 `app_ai_circuit_state`：0=关闭、1=打开、2=半开探测；确认 `app_ai_circuit_transitions_total` 最近是否出现 open→half_open→closed。
2. 按告警时间在 Java 日志中定位 `requestId` 和 `traceparent`，再用相同 Header 值查 Python 日志；不要复制 JWT、内部 Token、支付签名、完整画像或完整 Prompt。
3. 区分失败类型：连接/读取超时、Python 5xx、SSE 解析错误、内部身份认证失败。
4. 检查 Python 实例健康、资源使用和最近发布；随后检查 Java 到 Python 的 DNS、端口和网络策略。
5. 若只有 Redis 指标异常，按缓存故障处理；不要把 Redis 故障误判为 Python 故障。

## 3. 降级和恢复

- 熔断打开后，Java 自动使用现有安全 Fallback；不要通过重启 Java 强行清空熔断状态。
- 等待 `app.ai.circuit-breaker.wait-duration-ms` 后，Resilience4j 自动进入 HALF_OPEN 并允许探测请求。
- Python 恢复后，成功探测会使状态回到 CLOSED；用状态 Gauge、迁移 Counter 和成功请求指标共同确认恢复。
- 若 Python 发布导致持续失败，优先回滚 Python；若网络策略或内部身份配置错误，修复对应配置后等待自动探测。
- 紧急情况下可通过既有 `app.ai.circuit-breaker.enabled` 回滚开关调整调用路径，但必须保留 Fallback，且变更后验证同步与 SSE。

## 4. 交易影响

- 受影响：AI 对话质量、流式输出、自然语言解释和个性化推荐体验。
- 不应受影响：商品事实、价格、实时库存、订单创建、支付和退款。
- 若同时观察到交易错误，按独立交易事故升级，不要假设它由 AI 熔断引起。

## 5. 人工介入条件

出现以下任一情况需要人工介入：

- 超过两个熔断等待周期仍未出现成功 HALF_OPEN 探测；
- Fallback 错误率上升或返回了不可信商品、价格、库存信息；
- 内部身份认证持续失败，且无法确认 Java/Python 两侧配置版本一致；
- Python 恢复但状态持续 OPEN，或状态迁移与请求结果矛盾；
- AI 故障与订单、库存或支付错误同时发生。

处置结束后记录开始/恢复时间、根因、影响请求量、是否发生降级，以及用于关联的 request ID/traceparent 样例；记录中不得包含 Secret 或完整用户画像。
