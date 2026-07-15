# 第三周 Phase B：核心业务指标实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 为 AI、Redis、订单和支付建立低基数、可告警的 Micrometer 指标，并通过现有 Prometheus 端点导出。

**约束：** 指标标签只能来自固定操作/结果集合，不使用 userId、orderNo、requestId、SKU 等高基数字段；指标失败不得改变业务结果。

## Task 1：测试驱动指标语义

- [x] 先编写指标注册测试，覆盖 AI、Redis、订单、支付计数和耗时。
- [x] 确认指标门面尚不存在导致测试失败。
- [x] 实现单一 `ApplicationMetrics`，不创建一实现一接口。

## Task 2：接入关键路径

- [x] AI 同步/SSE 记录 success、error、circuit_open、fallback、候选数量和丢弃引用。
- [x] Redis 记录 get/set/delete/increment 的 hit、miss、success、error 和耗时。
- [x] 订单创建记录 created、replayed、failed；支付回调记录 invalid、duplicate、success、rejected。
- [x] 通过服务测试验证业务分支调用正确指标，不把动态业务 ID 放进标签。

## Task 3：文档与验证

- [x] 更新可观测性文档和 7.14 第三周实施状态。
- [x] 运行聚焦测试。
- [x] 运行 `backend\\mvnw.cmd verify` 和 `git diff --check`。
