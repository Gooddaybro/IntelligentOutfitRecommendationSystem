# Java 可观测性边界

## 1. 部署探针

| 端点 | 用途 | 依赖范围 |
| --- | --- | --- |
| `/actuator/health/liveness` | 判断 JVM/Spring 进程是否需要重启 | `livenessState`、`ping` |
| `/actuator/health/readiness` | 判断实例是否可以承接交易请求 | `readinessState`、数据库 |

Redis 是缓存和保护层，Python 是可降级的 AI 依赖，因此二者短暂失败不会让整个商城实例退出 readiness。
它们的故障应由独立指标、健康组件和告警反映，而不是通过摘除交易实例处理。

## 2. 暴露边界

应用只暴露 `health`、`info`、`prometheus` 三类 Actuator 端点，`env` 等敏感端点不暴露。
健康探针、构建信息和 Prometheus 抓取入口无需用户 JWT；生产部署必须通过管理网络、反向代理或防火墙限制 Prometheus 入口，不应把它当作商城公开业务 API。

`/actuator/info` 包含 Maven 构建版本，并从 `GIT_COMMIT` 环境变量读取部署 Commit；未注入时明确显示 `unknown`，不伪造版本来源。

## 3. 请求关联信任边界

`X-Request-Id` 只接受 1 到 128 个 ASCII 字母、数字以及 `._:-`。超长值、换行符和其他非法字符不会进入 MDC，服务端会重新生成 UUID，从源头避免日志注入和无界日志字段。

`traceparent` 只接受 W3C v00 的小写十六进制格式，且 trace ID 和 parent ID 不能全零。缺失或非法时 Java 生成新的 sampled traceparent。两者都会写回响应，并通过 MDC 传播到 SSE 有界线程池，再写入 Java→Python 同步和 SSE 请求 Header。

本阶段提供跨线程、跨 HTTP 边界的稳定关联标识，不引入采样器或 Trace 后端；因此它不等同于完整分布式追踪。需要跨度、父子 Span 和链路瀑布图时，再接入 Micrometer Tracing/OpenTelemetry。

## 4. 核心业务指标

| 指标 | 固定标签 | 语义 |
| --- | --- | --- |
| `app.ai.requests` | `mode`、`outcome` | Python 同步/SSE 成功、错误和熔断旁路 |
| `app.ai.request.duration` | `mode`、`outcome` | Java 调 Python 的终态耗时 |
| `app.ai.fallbacks` | `mode` | 同步或流式降级次数 |
| `app.ai.candidates` | 无 | Java 为 AI 请求组装的候选数量分布 |
| `app.ai.discarded.references` | 无 | Python 引用被 Java 可信候选校验丢弃的数量 |
| `app.ai.circuit.state` | 无 | 熔断状态数值：closed=0、open=1、half-open=2、disabled=3、forced-open=4、metrics-only=5 |
| `app.ai.circuit.transitions` | `from`、`to` | 熔断状态迁移次数，用于确认打开、半开探测和自动恢复 |
| `app.redis.commands` | `operation`、`outcome` | Redis hit、miss、success、error |
| `app.redis.command.duration` | `operation`、`outcome` | Redis 命令耗时 |
| `app.order.creation` | `operation`、`outcome` | 购物车/立即购买的创建、重放和失败 |
| `app.payment.callbacks` | `outcome` | 回调成功、验签失败、重复和业务拒绝 |
| `app.recommendation.funnel` | `stage` | exposure、click、favorite、cart、order、payment 推荐漏斗阶段 |

所有标签由 `ApplicationMetrics` 收敛到固定集合，未知值统一记为 `other`。禁止将 userId、orderNo、requestId、SKU、异常消息等高基数值写入标签。

## 5. 后续阶段

- Phase D 已建立 `assistant_recommendation` 与 `assistant_recommendation_item` 推荐快照，保存 request/thread、调用模式、规则版本、全部候选 SKU 和最终选中结果。
- Phase E 已把点击、收藏、加购、下单和支付事件绑定到 recommendationId，并提供 `Java 商城核心` 与 `AI 导购` 两个 Grafana Dashboard。

公开交互提供 recommendationId 时，Java 必须验证“当前用户 + 最终推荐 SPU/SKU”；其他用户或未被最终选择的商品不能伪造归因。交易埋点保持 best-effort：归因失败不会回滚购物车、订单或支付事实。购物车结算从 30 天内最近一次有效推荐加购继承 ID，支付从同订单/SKU 的订单创建事件继承 ID。

当前规则版本固定记录为 `java-rule-reranker-v1`。Java-Python v1 契约尚未提供模型、Prompt 和 RAG 索引版本，因此对应数据库字段保持 `NULL`，不使用推测值冒充真实版本。

AI 依赖异常处置见 [`docs/runbooks/ai-assistant-dependency-failure.md`](../runbooks/ai-assistant-dependency-failure.md)。
Dashboard 启动和 provisioning 见 [`observability/README.md`](../../observability/README.md)。
