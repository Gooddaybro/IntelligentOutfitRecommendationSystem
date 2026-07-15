# 第三周 Phase A：可观测性基础实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 先建立部署探针、Prometheus 抓取入口、最小构建信息和可信 request ID，为后续 AI/Redis/交易指标提供统一底座。

**边界：** 本阶段不提前实现推荐归因、Dashboard、Runbook 或 RabbitMQ；readiness 只依赖数据库等关键事实能力，Redis/Python 短暂失败不让商城整体摘流。

## Task 1：测试驱动 request ID 信任边界

- [x] 增加超长、非法字符 request ID 的失败测试。
- [x] 确认旧实现错误接受不可信 request ID。
- [x] 实现长度和字符集校验，非法值改为服务端 UUID。

## Task 2：测试驱动 Actuator 与 Prometheus

- [x] 增加 liveness、readiness、Prometheus 和端点暴露范围测试。
- [x] 确认未配置 Actuator 时测试失败。
- [x] 添加 Actuator/Prometheus 依赖、健康分组、必要端点暴露和安全放行。
- [x] 生成构建信息，并为部署 Commit 信息提供环境变量入口。

## Task 3：文档与验证

- [x] 记录第三周 Phase A 的责任边界和已验证范围。
- [x] 运行聚焦测试。
- [x] 运行 `backend\\mvnw.cmd verify` 和 `git diff --check`。
