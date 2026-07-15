# Java 模块边界 Phase C 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 消除 Payment、AfterSale 对 `OrderMapper` 的直接依赖，以订单应用服务统一保护订单表所有权、行锁和支付状态更新语义。

**约束：** 保持单体事务传播；不拆 Maven 模块；不引入单实现接口；跨模块不暴露 `SalesOrder`、`OrderItem` 持久化模型。

## Task 1：建立失败的架构门禁

- [x] 删除 ArchUnit 中两个临时跨 Mapper 白名单。
- [x] 运行 `ModuleArchitectureTests`，确认因 Payment/AfterSale 直接访问 `OrderMapper` 而失败。

## Task 2：测试驱动订单应用边界

- [x] 先编写 `OrderApplicationServiceTests`，覆盖订单锁定视图、明细视图和支付状态更新失败解释。
- [x] 运行测试，确认因边界尚不存在而失败。
- [x] 实现最小 `OrderApplicationService`，只暴露跨模块所需的不可变视图和命令。
- [x] 运行订单应用边界测试并通过。

## Task 3：迁移 Payment 与 AfterSale

- [x] 修改服务测试，使其依赖订单应用边界而不是 `OrderMapper`。
- [x] 修改生产服务并删除订单持久化模型导入。
- [x] 运行 Payment、AfterSale 与架构测试。

## Task 4：同步文档并全量验证

- [x] 更新模块边界文档和 7.14 架构设计实施状态。
- [x] 运行 `backend\\mvnw.cmd verify`。
- [x] 运行 `git diff --check` 并确认计划没有未完成任务。
