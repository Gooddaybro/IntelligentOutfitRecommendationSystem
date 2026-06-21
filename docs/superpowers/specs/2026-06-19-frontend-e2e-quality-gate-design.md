# 前端 E2E 质量门禁设计

本文定义 Intelligent Outfit Recommendation System 前端端到端测试的补齐方案。

前端已经具备 API client、SSE 解析和 commerce action 的单元测试，但完整购物流程主要依赖 `docs/frontend-backend-integration-test.md` 中的人工联调步骤。下一阶段应把关键用户路径沉淀为自动化浏览器测试。

当前状态：第一版 Playwright E2E 已落地。它使用前端页面和 API mock 验证用户主路径，不依赖本地 Java/Python 服务启动。

## 目标

把以下用户路径变成可重复验证的 E2E 质量门禁：

```text
登录/注册
-> AI 推荐页发起导购请求
-> 展示 AI 回答和推荐商品卡
-> 用户确认加入购物车或立即购买
-> 购物车结算或订单创建
-> mock 支付
-> UI 和后端状态一致
```

## 工具选择

推荐使用 Playwright。

理由：

- 能覆盖真实浏览器行为、登录态、本地存储、表单、弹窗和导航。
- 能通过 route mock 稳定 AI SSE 响应，避免 E2E 依赖真实大模型。
- 能在后续 CI 中无头运行。
- TypeScript 项目接入成本低。

## 测试边界

E2E 测试验证用户可见行为和跨模块协作，不替代已有单元测试。

应该覆盖：

- 登录态恢复。
- AI 推荐页基础输入和流式响应展示。
- 推荐卡片数据来自 Java 后端候选商品。
- 加入购物车必须经过确认弹窗。
- 立即购买必须经过确认弹窗。
- 购物车数量、结算、订单列表和 mock 支付状态。
- AI 服务失败时的受控错误提示或同步 fallback。

不应该覆盖：

- Java service 的所有业务分支。
- Python LangGraph 的节点路由细节。
- 真实模型答案质量。
- 大量样式像素级断言。

## 推荐测试模式

### 模式 A：Mock AI，真实 Java 后端

适合第一阶段。

```text
真实：Java 后端、MySQL、前端页面
Mock：Python AI 或 Java 转发到 Python 的 assistant 响应
```

目标是稳定验证购物主链路，不被模型和网络波动影响。

### 模式 B：真实 Java + 真实 Python，本地冒烟

适合手动或 nightly。

```text
真实：Java 后端、Python AI、MySQL、前端页面
```

目标是验证跨服务联通，不要求每次开发都跑。

### 模式 C：纯前端路由 mock

适合快速 UI 回归。

```text
真实：前端页面
Mock：所有 `/api/*` 请求
```

目标是低成本验证 UI 状态和交互，不验证后端。

## 第一批测试用例

### 1. Auth Smoke

**目标：** 用户能注册或登录，刷新后仍保持登录态。

验收：

- 未登录时显示登录/注册界面。
- 注册成功后进入主界面。
- 刷新页面后仍能看到顶部导航和用户名。
- 退出登录后回到认证界面。

### 2. AI Recommendation Smoke

**目标：** AI 推荐页能发起导购请求并渲染结果。

验收：

- 输入导购需求后，聊天区出现用户消息。
- assistant 区域出现 token 或最终答案。
- 推荐商品卡出现，且商品名、价格、库存来自后端候选数据。
- Python 或 assistant mock 返回空推荐时，页面不崩溃。

### 3. Add To Cart Requires Confirmation

**目标：** AI 推荐卡不能直接静默加购。

验收：

- 点击推荐卡上的加入购物车按钮后，出现确认弹窗。
- 取消后购物车数量不变。
- 确认后购物车数量增加。
- 购物车页面能看到对应 SKU 和数量。

### 4. Buy Now Requires Confirmation

**目标：** 立即购买不能绕过用户确认。

验收：

- 点击立即购买后，出现确认弹窗。
- 确认后跳转或切换到订单页面。
- 订单状态为未支付或等价状态。
- 前端不提交价格、金额或 userId。

### 5. Checkout And Mock Payment

**目标：** 用户能从购物车完成下单和 mock 支付。

验收：

- 购物车结算后生成订单。
- 订单列表显示新订单。
- mock 支付后订单状态更新。
- `mock-pay` 支付请求只提交 `orderNo`；当前订单页通用支付请求只提交 `orderNo` 和 `channel=MOCK`。

### 6. Assistant Failure Fallback

**目标：** AI 服务不可用时，页面显示可诊断错误，不影响商品、购物车、订单功能。

验收：

- assistant 请求失败时显示错误信息。
- 页面不丢失登录态。
- 商品浏览和购物车仍可继续使用。

## 推荐文件结构

```text
frontend/
├── playwright.config.ts
├── e2e/
│   ├── ai-shopping.spec.ts
│   ├── fixtures/
│   │   └── api.ts
```

已落地：

```text
frontend/playwright.config.ts
frontend/e2e/ai-shopping.spec.ts
frontend/e2e/fixtures/api.ts
```

## Package Scripts

建议新增：

```json
{
  "scripts": {
    "test:e2e": "playwright test",
    "test:e2e:headed": "playwright test --headed",
    "test:e2e:ui": "playwright test --ui"
  }
}
```

已落地：

```json
{
  "scripts": {
    "test:e2e": "playwright test",
    "test:e2e:headed": "playwright test --headed",
    "test:e2e:ui": "playwright test --ui"
  }
}
```

## Stable Selectors

前端应为关键控件补稳定选择器，避免测试依赖中文文案或 CSS 类名。

建议格式：

```tsx
data-testid="auth-username"
data-testid="auth-password"
data-testid="ai-chat-input"
data-testid="ai-chat-submit"
data-testid="recommendation-card"
data-testid="confirm-action-submit"
data-testid="cart-count"
data-testid="order-status"
```

选择器应只服务测试稳定性，不参与业务逻辑。

第一版已在认证表单、AI 聊天输入、推荐卡、确认弹窗、购物车、订单和导航关键控件上补充 `data-testid`。

## Assistant Mock Strategy

第一阶段优先 mock assistant streaming response。

推荐事件：

```text
thread -> token -> recommendation -> done
```

约束：

- SSE `data` 必须是单行 JSON。
- `recommendation` 中的 `spuIds` 必须能匹配 Java 商品候选。
- `done.answer` 可以为空，但页面必须保留已流式输出的 token。

## Local Run Prerequisites

纯前端 mock E2E 运行前需要：

- 前端依赖已安装。
- Playwright Chromium 已安装。

真实后端 E2E 运行前还需要：

- Java 后端可访问。
- MySQL 已启动并完成 Flyway migration。
- 前端 Vite dev server 可访问。
- 如果不 mock assistant，则 Python AI 服务可访问。

当前 Playwright `webServer` 会自动启动前端；Java/Python 服务第一阶段不需要启动。

## Verification Commands

开发完成后的最小验证：

```powershell
cd frontend
npm test -- --run
npm run build
npm run test:e2e
```

第一次运行 Playwright 前，如果本机没有浏览器缓存：

```powershell
cd frontend
npx playwright install chromium
```

如果 E2E 依赖 Java 后端，先运行：

```powershell
cd backend
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

## Risks

- 测试数据污染：需要随机用户名或专用测试清理策略。
- AI 响应波动：第一阶段用 mock 稳定主流程。
- 选择器不稳定：补 `data-testid`，不要依赖复杂 CSS selector。
- 启动链路复杂：第一阶段不强求 Playwright 自动启动所有服务。

## 第一版覆盖范围

已覆盖：

- 登录页面提交。
- 登录后恢复用户和购物车。
- AI 推荐页发送消息。
- mock SSE 返回 token、recommendation、done。
- 推荐卡展示后端候选商品。
- 点击加购后必须出现确认弹窗。
- 取消确认不会调用加购接口。
- 确认后才调用 `POST /api/cart/items`。
- 购物车页用 `POST /api/orders` 创建订单。
- 订单页用 `POST /api/payments` 和 `channel=MOCK` 完成 mock 支付。
- E2E 断言前端不会在这些请求里提交价格、金额、userId、订单状态或支付状态。

仍待补充：

- 使用真实 Java 后端的 E2E smoke。
- assistant 失败 fallback 的独立 E2E。
- 传统浏览页到加购的 E2E。
- buy-now 独立 E2E。
- CI 中的 E2E 执行策略。

## Approval Checklist

开发前确认：

- [x] 是否采用 Playwright。
- [x] 第一阶段是否允许 mock assistant response。
- [x] 是否先只覆盖 AI 推荐到加购的 smoke flow。
- [ ] 是否需要把 E2E 纳入 CI，还是先作为本地质量门禁。
