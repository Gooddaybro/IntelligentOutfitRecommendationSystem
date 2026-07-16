# 水木管理后台 F3 前端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不依赖新增 Java 接口的前提下完成可演示、可操作、可切换真实 API 的管理后台前端，覆盖数据概览、商品、分类、SKU/库存、订单、用户、经营分析和操作日志。

**Architecture:** 延续现有 `/admin/*` 独立壳层和 `api` Mock/HTTP 双适配。管理端新增一组明确的领域类型与 API 方法，页面只消费接口返回的事实；Mock 模式维护内存状态，API 模式请求预留的 `/api/admin/**` 契约且不自动回退。列表页共享轻量表格、筛选和状态组件，不引入新的状态管理库或图表依赖。

**Tech Stack:** React 18、TypeScript、React Router、Vite、Vitest、Testing Library、Playwright、CSS 自定义属性、Lucide React。

---

## 文件结构

```text
frontend/src/
├── features/admin/
│   ├── AdminDataTable.tsx          通用管理表格外壳与空状态
│   ├── AdminStatusBadge.tsx        商品、订单、用户等状态展示
│   └── adminFilters.ts             纯函数筛选与汇总
├── pages/admin/
│   ├── AdminProductsPage.tsx       商品查询、上下架与新增/编辑入口
│   ├── AdminProductFormPage.tsx    商品分区表单
│   ├── AdminCategoriesPage.tsx     两级分类、排序和启停
│   ├── AdminInventoryPage.tsx      SKU、库存预警与调整
│   ├── AdminOrdersPage.tsx         管理端订单查询与发货
│   ├── AdminUsersPage.tsx          用户查询与启停
│   ├── AdminAnalyticsPage.tsx      经营指标、趋势和热门商品
│   └── AdminAuditLogsPage.tsx      只读操作日志
├── shared/api/
│   ├── adminTypes.ts               管理端领域契约
│   ├── client.ts                   HTTP 管理接口
│   └── mockApi.ts                  管理端演示状态
└── styles/admin.css                后台表格、表单和响应式样式
```

---

### Task 1：建立管理端领域契约与 Mock 状态

**Files:**
- Create: `frontend/src/shared/api/adminTypes.ts`
- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/shared/api/mockApi.ts`
- Modify: `frontend/src/shared/api/mockApi.test.ts`

- [ ] **Step 1：先写失败测试**

在 `mockApi.test.ts` 中验证：概览返回真实统计值；商品上下架后列表状态改变；库存调整记录原因并更新可售库存。

```ts
const before = await mockApi.adminProducts();
await mockApi.adminSetProductStatus(before[0].spuId, "OFF_SHELF");
expect((await mockApi.adminProducts())[0].status).toBe("OFF_SHELF");

const sku = (await mockApi.adminInventory())[0];
await mockApi.adminAdjustInventory(sku.skuId, sku.availableStock + 5, "到货入库");
expect((await mockApi.adminInventory())[0].availableStock).toBe(sku.availableStock + 5);
```

- [ ] **Step 2：运行测试确认 RED**

Run: `cd frontend && npm test -- --run mockApi.test.ts`

Expected: FAIL，提示管理端 API 方法不存在。

- [ ] **Step 3：实现最小契约**

`adminTypes.ts` 定义 `AdminOverview`、`AdminProduct`、`AdminCategory`、`AdminSku`、`AdminOrder`、`AdminUser`、`AdminAnalytics`、`AdminAuditLog`。写接口返回更新后的对象或列表；金额使用 `number` 仅作为 JSON 十进制值展示，不在前端重新计算交易事实。

HTTP 路径固定为：

```text
GET  /api/admin/overview
GET  /api/admin/products
POST /api/admin/products
PUT  /api/admin/products/{spuId}
POST /api/admin/products/{spuId}/status
GET  /api/admin/categories
PUT  /api/admin/categories/{id}
GET  /api/admin/inventory
POST /api/admin/inventory/{skuId}/adjustments
GET  /api/admin/orders
POST /api/admin/orders/{orderNo}/ship
GET  /api/admin/users
POST /api/admin/users/{userId}/status
GET  /api/admin/analytics
GET  /api/admin/audit-logs
```

- [ ] **Step 4：运行测试确认 GREEN**

Run: `cd frontend && npm test -- --run mockApi.test.ts`

Expected: PASS。

- [ ] **Step 5：中文提交**

```bash
git add frontend/src/shared/api
git commit -m "前端：建立管理后台领域契约"
```

---

### Task 2：接入真实概览指标与后台路由

**Files:**
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/pages/AdminDashboardPage.tsx`
- Modify: `frontend/src/pages/AdminDashboardPage.test.tsx`
- Create: `frontend/src/features/admin/AdminStatusBadge.tsx`

- [ ] **Step 1：写失败测试**

Mock `api.adminOverview()`，断言页面展示在售商品数、SKU 数、库存预警、待发货和支付金额，而不是“—”与占位说明。

- [ ] **Step 2：运行 RED**

Run: `cd frontend && npm test -- --run AdminDashboardPage.test.tsx`

Expected: FAIL，概览仍是占位数据。

- [ ] **Step 3：实现概览加载与错误/空状态**

页面首次进入请求一次数据；加载时显示骨架文本，失败时展示“重新加载”，无趋势数据时显示明确空状态。新增路由分别指向八个管理页面，不再使用通配占位页。

- [ ] **Step 4：运行 GREEN**

Run: `cd frontend && npm test -- --run AdminDashboardPage.test.tsx`

Expected: PASS。

- [ ] **Step 5：中文提交**

```bash
git add frontend/src/app frontend/src/pages/AdminDashboardPage* frontend/src/features/admin
git commit -m "前端：接入管理后台数据概览"
```

---

### Task 3：完成商品与分类管理

**Files:**
- Create: `frontend/src/features/admin/AdminDataTable.tsx`
- Create: `frontend/src/features/admin/adminFilters.ts`
- Create: `frontend/src/features/admin/adminFilters.test.ts`
- Create: `frontend/src/pages/admin/AdminProductsPage.tsx`
- Create: `frontend/src/pages/admin/AdminProductFormPage.tsx`
- Create: `frontend/src/pages/admin/AdminCategoriesPage.tsx`
- Modify: `frontend/src/app/App.tsx`

- [ ] **Step 1：写筛选与状态操作失败测试**

验证关键词、分类、状态共同过滤商品；停用分类前显示关联商品数量；商品上下架调用对应 API 并刷新当前行。

- [ ] **Step 2：运行 RED**

Run: `cd frontend && npm test -- --run adminFilters.test.ts AdminProductsPage`

Expected: FAIL，筛选函数和页面尚不存在。

- [ ] **Step 3：实现商品列表与分区表单**

列表列固定为主图/商品、SPU 编码、分类、价格区间、SKU 数、总库存、状态、操作。表单分为基础信息、分类标签、商品图片、SKU 库存、详情和上架设置；保存调用新增或编辑 API，未保存离开时使用 `beforeunload` 提醒。

- [ ] **Step 4：实现两级分类管理**

仅允许根分类和二级分类；编辑父级时排除自身与子级；启停操作弹窗展示 `productCount` 影响范围。

- [ ] **Step 5：运行 GREEN 与构建**

Run: `cd frontend && npm test -- --run adminFilters.test.ts AdminProductsPage && npm run build`

Expected: PASS。

- [ ] **Step 6：中文提交**

```bash
git add frontend/src/features/admin frontend/src/pages/admin frontend/src/app/App.tsx
git commit -m "前端：完成商品与分类管理页面"
```

---

### Task 4：完成 SKU 与库存管理

**Files:**
- Create: `frontend/src/pages/admin/AdminInventoryPage.tsx`
- Create: `frontend/src/pages/admin/AdminInventoryPage.test.tsx`
- Modify: `frontend/src/styles/admin.css`

- [ ] **Step 1：写失败测试**

断言低库存行有“库存预警”；调整弹窗必须输入新库存和原因；提交后数量与最近调整摘要更新。

- [ ] **Step 2：运行 RED**

Run: `cd frontend && npm test -- --run AdminInventoryPage.test.tsx`

Expected: FAIL，页面不存在。

- [ ] **Step 3：实现库存表格与调整弹窗**

表格展示 SKU 编码、商品、颜色、尺码、售价、可售库存、阈值和状态。调整只提交 `targetStock` 与 `reason`，不在前端直接修改数组；以 API 返回结果替换当前行。

- [ ] **Step 4：运行 GREEN**

Run: `cd frontend && npm test -- --run AdminInventoryPage.test.tsx`

Expected: PASS。

- [ ] **Step 5：中文提交**

```bash
git add frontend/src/pages/admin/AdminInventoryPage* frontend/src/styles/admin.css
git commit -m "前端：完成SKU与库存管理"
```

---

### Task 5：完成订单与用户管理

**Files:**
- Create: `frontend/src/pages/admin/AdminOrdersPage.tsx`
- Create: `frontend/src/pages/admin/AdminOrdersPage.test.tsx`
- Create: `frontend/src/pages/admin/AdminUsersPage.tsx`
- Create: `frontend/src/pages/admin/AdminUsersPage.test.tsx`

- [ ] **Step 1：写订单发货失败测试**

只有 `PAID` 且 `availableActions` 包含 `SHIP` 的订单显示发货入口；承运商或运单号为空时不可提交；成功后状态变为 `SHIPPED`。

- [ ] **Step 2：写用户启停失败测试**

用户列表不显示密码字段；禁用操作必须二次确认并展示用户名；成功后状态变为 `DISABLED`。

- [ ] **Step 3：运行 RED**

Run: `cd frontend && npm test -- --run AdminOrdersPage AdminUsersPage`

Expected: FAIL，页面不存在。

- [ ] **Step 4：实现订单和用户页面**

订单按订单号、用户、状态过滤，详情抽屉展示商品、金额、地址和支付摘要。用户按 ID、用户名、昵称和状态过滤，详情展示注册时间与订单摘要，不提供衣橱画像编辑。

- [ ] **Step 5：运行 GREEN**

Run: `cd frontend && npm test -- --run AdminOrdersPage AdminUsersPage`

Expected: PASS。

- [ ] **Step 6：中文提交**

```bash
git add frontend/src/pages/admin/AdminOrdersPage* frontend/src/pages/admin/AdminUsersPage*
git commit -m "前端：完成订单与用户管理"
```

---

### Task 6：完成经营分析与操作日志

**Files:**
- Create: `frontend/src/pages/admin/AdminAnalyticsPage.tsx`
- Create: `frontend/src/pages/admin/AdminAuditLogsPage.tsx`
- Create: `frontend/src/pages/admin/AdminAnalyticsPage.test.tsx`

- [ ] **Step 1：写失败测试**

断言经营分析展示统计范围、订单量、成交额、转化漏斗和热门商品；操作日志展示管理员、动作、对象、结果和时间，且没有编辑/删除按钮。

- [ ] **Step 2：运行 RED**

Run: `cd frontend && npm test -- --run AdminAnalyticsPage.test.tsx`

Expected: FAIL，页面不存在。

- [ ] **Step 3：实现无依赖可视化**

趋势使用语义化列表与 CSS 柱形条，漏斗展示后端返回的曝光、点击、加购、成交值及口径说明。无数据时显示空状态，不生成随机数。

- [ ] **Step 4：运行 GREEN**

Run: `cd frontend && npm test -- --run AdminAnalyticsPage.test.tsx`

Expected: PASS。

- [ ] **Step 5：中文提交**

```bash
git add frontend/src/pages/admin/AdminAnalyticsPage* frontend/src/pages/admin/AdminAuditLogsPage.tsx
git commit -m "前端：完成经营分析与操作日志"
```

---

### Task 7：后台响应式与全链路验收

**Files:**
- Create: `frontend/e2e/admin-console.spec.ts`
- Modify: `frontend/e2e/fixtures/api.ts`
- Modify: `frontend/src/styles/admin.css`
- Modify: `frontend/src/styles/index.css`

- [ ] **Step 1：补管理端 E2E**

覆盖管理员登录、概览、商品筛选与下架、库存调整、订单发货、用户禁用，以及普通用户访问 `/admin` 被重定向。

- [ ] **Step 2：验证三档布局**

在 1440、900、390 三档断言无页面级横向溢出；900 保持全部管理操作；390 的复杂表格放入局部滚动区并保留筛选、状态和主要操作。

- [ ] **Step 3：运行完整验证**

Run:

```bash
cd frontend
npm test -- --run
npm run test:e2e
npm run build
```

Expected: 单元测试、全部 Playwright 用例和生产构建均通过。

- [ ] **Step 4：中文提交**

```bash
git add frontend/e2e frontend/src/styles
git commit -m "测试：完成管理后台F3验收"
```

---

## 自检结论

- 八个后台入口均有对应任务，不再保留通配占位页面。
- 商品、库存、订单和用户写操作全部以 API 返回值更新页面，Mock 只用于显式演示模式。
- 管理权限仍由 Java 后端作为最终边界；前端路由守卫只改善体验。
- 不引入图表库、表格库或全局状态库，避免为首期管理台增加不必要复杂度。
- 本计划完成后进入后端 B1–B4：先实现管理鉴权和概览/商品契约，再实现库存、订单、用户与审计。
