# 水木商城 F1 收尾与 F2 前端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在显式 Mock 模式中完成“商品决策 → 购物袋 → 确认订单 → 支付结果 → 订单详情”的可演示闭环，并把衣橱画像升级为完整个人中心。

**Architecture:** 延续现有 React Router 双壳层和 `api` Mock/HTTP 双适配方式。新增地址、结算预览、订单履约和收藏的共享 TypeScript 契约；流程状态通过 URL 参数和后端/Mock 返回值传递，不引入全局状态库。正式 API 模式只调用预留 Java 契约，不回退演示数据。

**Tech Stack:** React 18、TypeScript、React Router、Vite、Vitest、Testing Library、Playwright、CSS 自定义属性。

---

## 文件结构

```text
frontend/src/
├── app/
│   └── App.tsx                         F2 路由和流程跳转
├── features/
│   ├── cart/cartValidation.ts          购物袋有效性和金额纯函数
│   └── checkout/checkoutSelection.ts   URL 中的结算 SKU 解析
├── pages/
│   ├── CartPage.tsx                    购物袋编辑和去结算
│   ├── CheckoutPage.tsx                地址、结算摘要和提交订单
│   ├── PaymentResultPage.tsx           演示支付与结果
│   ├── OrderDetailPage.tsx             订单进度、物流和售后入口
│   ├── ProfileCenterPage.tsx           个人中心壳层
│   ├── AddressBookPage.tsx             地址管理
│   └── FavoritesPage.tsx               收藏列表
├── shared/api/
│   ├── types.ts                        F2 领域契约
│   ├── client.ts                       HTTP 契约
│   └── mockApi.ts                      内存演示状态
└── styles/commerce.css                 F2 页面和响应式样式
```

### Task 1：固化 F2 类型与纯函数

**Files:**
- Modify: `frontend/src/shared/api/types.ts`
- Create: `frontend/src/features/cart/cartValidation.ts`
- Create: `frontend/src/features/cart/cartValidation.test.ts`
- Create: `frontend/src/features/checkout/checkoutSelection.ts`
- Create: `frontend/src/features/checkout/checkoutSelection.test.ts`

- [ ] 先写失败测试：缺货项不可结算、金额只统计有效选中项、SKU 查询参数可稳定解析。
- [ ] 运行 `npm test -- --run cartValidation checkoutSelection`，确认因函数不存在失败。
- [ ] 增加 `Address`、`CheckoutPreview`、`ShipmentSummary`、`AfterSaleSummary` 类型和最小纯函数。
- [ ] 重跑定向测试并提交：`前端：建立结算与履约领域契约`。

### Task 2：扩展 Mock/API 双适配

**Files:**
- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/shared/api/mockApi.ts`
- Modify: `frontend/src/shared/api/mockApi.test.ts`

- [ ] 先写失败测试：地址增删改、结算预览、提交订单、支付后订单状态变化。
- [ ] 为 HTTP 模式预留 `/api/addresses`、`/api/checkout/preview`、订单状态动作契约。
- [ ] 让 Mock 状态产生可见变化，且金额从购物袋重新计算。
- [ ] 运行 Mock API 测试并提交：`前端：补充结算履约演示数据`。

### Task 3：购物袋升级为结算入口

**Files:**
- Modify: `frontend/src/pages/CartPage.tsx`
- Create: `frontend/src/pages/CartPage.test.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/styles/commerce.css`

- [ ] 先写失败测试：缺货项禁选、数量不能超过库存、按钮跳转确认订单而非直接创建。
- [ ] 实现有效性提示、选择汇总和 `/app/checkout?skuIds=` 跳转。
- [ ] 验证空购物袋与混合有效/失效状态。
- [ ] 提交：`前端：完善购物袋结算决策`。

### Task 4：确认订单与支付结果

**Files:**
- Create: `frontend/src/pages/CheckoutPage.tsx`
- Create: `frontend/src/pages/CheckoutPage.test.tsx`
- Create: `frontend/src/pages/PaymentResultPage.tsx`
- Create: `frontend/src/pages/PaymentResultPage.test.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/styles/commerce.css`

- [ ] 先写失败测试：没有地址不能提交、显示商品/运费/总额、提交后进入支付结果。
- [ ] 实现地址选择、结算预览、库存失效原因和防重复提交。
- [ ] 实现演示支付成功/失败展示，并提供订单详情和继续购物入口。
- [ ] 提交：`前端：完成确认订单与支付结果页面`。

### Task 5：订单详情与履约动作

**Files:**
- Create: `frontend/src/pages/OrderDetailPage.tsx`
- Create: `frontend/src/pages/OrderDetailPage.test.tsx`
- Modify: `frontend/src/pages/OrdersPage.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/styles/commerce.css`

- [ ] 先写失败测试：订单明细、地址快照、状态进度和状态允许的动作。
- [ ] 实现取消、确认收货、物流摘要和售后入口。
- [ ] 订单列表可进入详情页。
- [ ] 提交：`前端：增加订单详情与履约入口`。

### Task 6：完整个人中心

**Files:**
- Create: `frontend/src/pages/ProfileCenterPage.tsx`
- Create: `frontend/src/pages/AddressBookPage.tsx`
- Create: `frontend/src/pages/FavoritesPage.tsx`
- Modify: `frontend/src/pages/ProfilePreferencesPage.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/styles/commerce.css`

- [ ] 先写失败测试：个人中心包含资料、衣橱画像、收藏、地址和安全入口。
- [ ] 建立嵌套路由；保留现有资料/身体数据/偏好能力为衣橱画像页。
- [ ] 地址和收藏在 Mock 模式可新增、删除并立即反馈。
- [ ] 提交：`前端：落位完整个人中心`。

### Task 7：F1 搜索筛选与收藏收尾

**Files:**
- Modify: `frontend/src/pages/ProductBrowsePage.tsx`
- Modify: `frontend/src/pages/ProductBrowsePage.test.tsx`
- Modify: `frontend/src/pages/ProductDetailPage.tsx`
- Modify: `frontend/src/shared/api/mockApi.ts`

- [ ] 先写失败测试：分类、价格排序和关键字共同影响结果，收藏按钮有状态反馈。
- [ ] 把装饰筛选按钮改为可工作的紧凑筛选控件。
- [ ] 商品详情收藏写入 Mock/API 契约。
- [ ] 提交：`前端：完成商品筛选与收藏闭环`。

### Task 8：响应式与全链路验收

**Files:**
- Modify: `frontend/e2e/ai-shopping.spec.ts`
- Create: `frontend/e2e/customer-checkout.spec.ts`
- Modify: `frontend/src/styles/commerce.css`

- [ ] 增加“商品详情 → 购物袋 → 确认订单 → 支付 → 订单详情”E2E。
- [ ] 在 1440、900、390 三档检查关键页面无横向溢出和操作遮挡。
- [ ] 运行 `npm test -- --run`、`npm run test:e2e`、`npm run build`。
- [ ] 提交：`测试：完成商城 F2 前端链路验收`。

## 自检结论

- F2 的价格、库存和订单事实均来自 API/Mock repository 返回值，页面不接受自报金额。
- URL 只保存 SKU 标识，不保存价格和库存事实。
- Mock 模式始终显示“前端演示数据”，API 模式不自动降级。
- 本计划不修改 Java 和数据库；F3 管理后台完成并验收后再进入 B1–B4。

