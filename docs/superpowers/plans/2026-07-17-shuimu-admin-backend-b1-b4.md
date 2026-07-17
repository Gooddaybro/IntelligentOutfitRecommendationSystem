# 水木管理后台后端 B1-B4 实施计划

> 执行要求：继续使用 TDD。每个后端接口先写 MockMvc/服务测试，确认 RED，再实现最小代码，最后运行相关测试与构建。提交信息使用中文。

**目标：** 将前端 F3 管理后台从 Mock 契约逐步接到 Java 后端真实 `/api/admin/**` 接口，保证管理权限由后端作为最终边界，前端只消费后端事实。

**接口边界：** 后端优先兼容前端已实现的管理端契约：`overview`、`products`、`categories`、`inventory`、`orders`、`users`、`analytics`、`audit-logs`。首期不引入独立管理系统、不接真实支付网关、不实现复杂售后审批。

---

## B1：管理鉴权、概览和商品契约

**范围：**
- `GET /api/admin/overview`
- `GET /api/admin/products`
- `POST /api/admin/products/{spuId}/status`
- 管理端接口必须校验 `ADMIN`/`ROLE_ADMIN`，普通用户返回 403。

**验收：**
- 管理员能获取概览指标和商品列表。
- 商品上下架后，列表状态变化。
- 普通用户访问 `/api/admin/**` 被拒绝。

---

## B2：分类、SKU 与库存调整

**范围：**
- `GET /api/admin/categories`
- `PUT /api/admin/categories/{id}`
- `GET /api/admin/inventory`
- `POST /api/admin/inventory/{skuId}/adjustments`

**验收：**
- 分类启停返回影响范围。
- 库存调整只接收目标库存和原因，返回后端更新后的 SKU。
- 调整记录保留原因、操作人和时间。

---

## B3：订单和用户管理

**范围：**
- `GET /api/admin/orders`
- `POST /api/admin/orders/{orderNo}/ship`
- `GET /api/admin/users`
- `POST /api/admin/users/{userId}/status`

**验收：**
- 只有待发货订单可发货。
- 发货后订单状态变为已发货并返回物流信息。
- 用户禁用/启用由后端落库或领域状态维护。

---

## B4：经营分析和审计日志

**范围：**
- `GET /api/admin/analytics`
- `GET /api/admin/audit-logs`
- 商品、库存、订单、用户管理操作写入审计日志。

**验收：**
- 经营分析只返回后端统计事实。
- 审计日志只读，不提供删除/编辑接口。
- 前端 F3 全链路可切换真实 API 演示。
