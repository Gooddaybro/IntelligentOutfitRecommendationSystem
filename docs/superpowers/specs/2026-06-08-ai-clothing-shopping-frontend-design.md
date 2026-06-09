# Intelligent Outfit Recommendation System Frontend Design

## 目的

本文定义 `Intelligent Outfit Recommendation System` 的前端开发方案和仓库目录演进方案。当前项目已经具备 Java 后端接口、Python AI agent 联动、AI 同步/流式问答、商品、购物车、立即购买、订单和 mock 支付能力；下一阶段要补齐一个以 AI 导购为主、传统浏览为辅的前端。

本文只定义开发边界和模块设计，不执行目录迁移，不创建前端工程，也不修改 Java 后端代码。

## 产品定位

第一版前端不是“普通商城加聊天框”，而是“AI 为主的服装导购商城，同时保留传统浏览入口”。

核心体验：

- 用户可以直接和 AI 对话，说出场景、风格、预算、颜色、身材偏好。
- AI 返回文字解释和推荐商品卡片。
- 用户点击推荐卡片后，可以查看详情、选择 SKU、加入购物车或立即购买。
- AI 只能提出建议，不能绕过用户同意执行加购、下单或支付。
- 用户也可以进入传统浏览页，自行搜索、筛选、查看商品，并让 AI 在旁边辅助比较、搭配和选尺码。

## 仓库目标结构

保持当前项目名称 `Intelligent Outfit Recommendation System` 不变，把当前 Git 仓库根目录作为全栈项目根目录。目标只是把现有 Java 后端归入 `backend/`，并在同级新增 `frontend/`，不额外创建新的总目录。

目标结构：

```text
Intelligent Outfit Recommendation System/
├── backend/
│   ├── src/main/java
│   ├── src/main/resources
│   ├── src/test/java
│   ├── src/test/resources
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── .mvn/
│   └── checkstyle.xml
│
├── frontend/
│   ├── src/
│   ├── package.json
│   ├── vite.config.ts
│   └── index.html
│
├── docs/
│   ├── backend-feature-mapping.md
│   ├── api-testing-with-reqable.md
│   └── superpowers/
│
├── docker-compose.yml
├── README.md
└── .github/
```

目录职责：

- `backend/`：Java Spring Boot 后端。迁移后 Maven 命令在该目录执行。
- `frontend/`：前端单页应用。第一版建议使用 React、TypeScript、Vite。
- `docs/`：系统开发文档、接口文档、设计文档和测试说明。
- `docker-compose.yml`：保留在仓库根目录，作为全栈本地依赖入口。
- `.github/`：保留在仓库根目录，后续 CI 需要把后端工作目录改为 `backend/`，并新增前端构建检查。

## 目录迁移边界

目录迁移应作为一个独立步骤处理，不和前端业务开发混在同一个变更里。

迁移时移动到 `backend/` 的内容：

- `src/`
- `pom.xml`
- `mvnw`
- `mvnw.cmd`
- `.mvn/`
- `checkstyle.xml`
- Java 后端直接依赖的说明文件，例如后端专用 `HELP.md`

保留在根目录的内容：

- `.git/`
- `.github/`
- `.gitignore`
- `.gitattributes`
- `docs/`
- `docker-compose.yml`
- 根级 `README.md`
- 后续新增的 `frontend/`

迁移后命令变化：

```powershell
cd backend
.\mvnw.cmd verify
```

迁移不改变：

- Java package 名。
- Spring Boot 应用名。
- 数据库表结构。
- API 路径。
- Python agent 调用 Java 后端的 URL 语义。

## 技术栈建议

第一版推荐：

- React 19 或当前稳定 React 版本。
- TypeScript。
- Vite。
- React Router。
- TanStack Query 或轻量自封装 API hooks。
- Zustand 或 Redux Toolkit 管理 auth、cart、assistant UI 状态。
- CSS Modules、Tailwind CSS 或组件库二选一；第一版建议使用 Tailwind CSS 加少量自定义组件。

选择 React 的原因：

- AI 流式对话、推荐卡片、购物车抽屉和确认动作适合组件化组合。
- Vite 启动快，前后端分离联调成本低。
- TypeScript 能把后端 API 响应、前端动作和交易确认边界表达清楚。

如果后续决定改用 Vue 3，业务模块边界保持不变，只替换页面和状态管理实现。

## 信息架构

第一版页面：

```text
/login
  登录页

/register
  注册页

/ai
  AI 推荐页，以聊天为主，推荐商品和购物车在旁边辅助

/browse
  传统浏览页，以商品搜索和列表为主，AI 助手在旁边辅助

/cart
  购物车页

/orders
  订单列表页

/orders/:orderNo
  订单详情和 mock 支付页
```

导航结构：

- 顶部导航：AI 推荐、浏览商品、购物车、订单、用户入口。
- AI 推荐页默认作为登录后的主入口。
- 传统浏览页保留完整商城搜索能力。
- 购物车可以作为独立页面，也可以在 AI 推荐页和浏览页以右侧抽屉展示摘要。

## 页面设计

### AI 推荐页

定位：用户通过自然语言表达需求，由 AI 推荐商品。

布局：

```text
┌─────────────────────────────────────────────┐
│ 顶部导航                                     │
├───────────────────────────────┬─────────────┤
│ AI 对话主区域                  │ 推荐/购物车侧栏 │
│                               │             │
│ 用户消息                       │ 推荐卡片列表   │
│ AI 流式回复                    │ 购物车摘要     │
│ 推荐理由                       │ 确认动作面板   │
│                               │             │
│ 输入框                         │             │
└───────────────────────────────┴─────────────┘
```

核心交互：

- 用户输入需求。
- 前端调用 `POST /api/assistant/chat/stream`。
- 前端通过 `fetch` 读取 `text/event-stream`，逐步展示 AI 回复。
- AI 返回推荐商品引用后，前端把 SPU/SKU 引用解析为商品卡片。
- 用户点击推荐卡片，进入 SKU 选择和确认动作。

AI 推荐页中，商品卡片是对话结果的一部分，但交易动作必须由用户确认。

### 传统浏览页

定位：用户主动搜索和筛选商品，AI 在旁边辅助判断。

布局：

```text
┌─────────────────────────────────────────────┐
│ 顶部导航 + 搜索框                             │
├───────────┬─────────────────────┬───────────┤
│ 筛选栏     │ 商品列表              │ AI 助手侧栏 │
│           │ 商品卡片网格           │           │
│           │ 分页/加载更多          │ 当前商品上下文 │
└───────────┴─────────────────────┴───────────┘
```

核心交互：

- 用户通过关键词、分类、风格、季节、预算等条件搜索商品。
- 用户点击商品卡片查看详情或直接选择 SKU。
- AI 侧栏可以基于当前商品、当前筛选条件和用户问题给出建议。
- 第一版不要求后端新增“当前商品上下文”字段；前端可以把当前商品名称、类别、颜色、价格和用户问题拼成 message 发送给 AI。

传统浏览页中，AI 是辅助，不替代商品列表主流程。

## 业务模块拆分

推荐前端源码结构：

```text
frontend/src/
├── app/
│   ├── router.tsx
│   ├── App.tsx
│   └── providers.tsx
│
├── shared/
│   ├── api/
│   ├── components/
│   ├── hooks/
│   ├── types/
│   └── utils/
│
├── features/
│   ├── auth/
│   ├── catalog/
│   ├── assistant/
│   ├── recommendation/
│   ├── commerce-action/
│   ├── cart/
│   ├── order/
│   └── payment/
│
└── pages/
    ├── LoginPage.tsx
    ├── RegisterPage.tsx
    ├── AiShoppingPage.tsx
    ├── ProductBrowsePage.tsx
    ├── CartPage.tsx
    ├── OrdersPage.tsx
    └── OrderDetailPage.tsx
```

模块职责：

- `auth`：登录、注册、token 保存、刷新和退出。
- `catalog`：商品搜索、商品详情、SKU 选择、商品卡片。
- `assistant`：AI 同步/流式对话、消息列表、SSE 解析、会话历史。
- `recommendation`：把 AI 推荐引用解析成可展示的商品卡片。
- `commerce-action`：把 AI 建议转成用户确认动作，再调用购物车或订单 API。
- `cart`：购物车列表、加购、改数量、删除、清空、购物车摘要。
- `order`：购物车结算、立即购买、订单列表、订单详情、取消订单。
- `payment`：mock 支付，未来替换或扩展真实支付。

## 关键组件

共享组件：

- `ProductCard`：商品基础卡片。
- `RecommendationCard`：带 AI 推荐理由和确认按钮的商品卡片。
- `SkuSelector`：颜色、尺码、数量选择。
- `ChatPanel`：消息列表和输入框。
- `StreamingAssistantMessage`：流式展示 AI 回复。
- `CartDrawer`：右侧购物车抽屉。
- `CartSummary`：购物车金额和数量摘要。
- `ConfirmActionDialog`：用户确认加购、立即购买、支付等动作。
- `OrderSummary`：订单金额、商品快照和状态。
- `PaymentPanel`：mock 支付入口。

组件边界：

- `ProductCard` 不直接调用加购或下单 API，只触发用户意图事件。
- `RecommendationCard` 可以展示 AI 推荐理由，但不能直接执行交易动作。
- `ConfirmActionDialog` 是所有交易动作的最后确认边界。
- `CartDrawer` 只展示和管理购物车，不处理 AI 对话状态。
- `ChatPanel` 只处理对话输入、输出和流式状态，不直接改购物车。

## AI 到交易动作的确认层

前端必须实现一个明确的 `commerce-action` 确认层。

动作模型：

```text
ADD_TO_CART
  来源：用户点击商品卡片或推荐卡片
  执行：POST /api/cart/items

BUY_NOW
  来源：用户点击立即购买
  执行：POST /api/orders/buy-now

CHECKOUT_CART
  来源：用户在购物车确认结算
  执行：POST /api/orders

MOCK_PAY
  来源：用户在订单详情页确认支付
  执行：POST /api/payments/mock-pay
```

确认流程：

```text
AI 推荐商品
-> 前端展示推荐卡片
-> 用户点击加入购物车或立即购买
-> 前端展示确认弹窗或确认面板
-> 用户确认
-> 前端调用后端交易 API
-> 前端展示成功或失败结果
```

禁止流程：

```text
AI 返回推荐
-> 前端或后端自动加入购物车
```

设计原因：

- AI 推荐不等于用户授权。
- 用户必须确认具体 SKU、数量和价格。
- 后端仍然负责最终价格计算、库存锁定和订单状态。

## 后端 API 对接

### Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/users/me`

前端策略：

- 登录后保存 `accessToken` 和 `refreshToken`。
- 普通 API 请求统一附加 `Authorization: Bearer <accessToken>`。
- 访问受保护页面前检查登录状态。
- 收到 `401` 后尝试刷新 token；刷新失败则回到登录页。

### Catalog

- `GET /api/products`
- `GET /api/products/{spuId}`
- `GET /api/products/recommendation-candidates`

前端策略：

- 商品浏览页使用 `GET /api/products`。
- 推荐卡片缺少详情时，使用 `GET /api/products/{spuId}` 补齐图片、价格和 SKU 信息。
- SKU 选择必须在前端确认后再进入加购或立即购买。

### Assistant

- `POST /api/assistant/chat`
- `POST /api/assistant/chat/stream`
- `POST /api/conversations`
- `GET /api/conversations`
- `GET /api/conversations/{threadId}/messages`
- `DELETE /api/conversations/{threadId}`

前端策略：

- AI 推荐页优先使用 `POST /api/assistant/chat/stream`。
- 因为流式接口是 POST，浏览器原生 `EventSource` 不适合第一版；前端使用 `fetch` + `ReadableStream` 读取 SSE。
- 流式事件分为 `meta`、`token`、`done`、`error`。
- `token` 用于逐字更新当前 AI 消息。
- `done` 后解析推荐引用，并生成推荐卡片。
- 如果流式失败，可以降级使用 `POST /api/assistant/chat`。

### Cart

- `GET /api/cart/items`
- `POST /api/cart/items`
- `PUT /api/cart/items/{skuId}`
- `DELETE /api/cart/items/{skuId}`
- `DELETE /api/cart/items`

前端策略：

- AI 推荐页和传统浏览页共用购物车摘要。
- 加购必须来自用户确认。
- 加购成功后刷新购物车缓存。

### Order

- `POST /api/orders`
- `POST /api/orders/buy-now`
- `GET /api/orders`
- `GET /api/orders/{orderNo}`
- `POST /api/orders/{orderNo}/cancel`

前端策略：

- 购物车结算调用 `POST /api/orders`，请求体只传 `source=CART` 和选中 `skuIds`。
- 立即购买调用 `POST /api/orders/buy-now`，请求体只传 `skuId` 和 `quantity`。
- 前端不传金额、订单状态或用户 ID。
- 下单成功后跳转订单详情页。

### Payment

- `POST /api/payments/mock-pay`

前端策略：

- 第一版只支持 mock 支付。
- 用户在订单详情页点击确认支付后调用接口。
- 支付成功后刷新订单详情。
- 真实支付后续作为独立功能扩展。

## AI 推荐卡片数据流

第一版数据流：

```text
用户输入需求
-> POST /api/assistant/chat/stream
-> 前端展示 AI 流式文字
-> done 事件返回推荐商品引用
-> recommendation 模块拉取商品详情
-> 展示 RecommendationCard
-> 用户选择 SKU 和数量
-> ConfirmActionDialog
-> cart/order API
```

如果 AI 只返回 `spuId`：

- 前端展示 SPU 级商品卡片。
- 用户必须打开 SKU 选择器。
- 用户确认颜色、尺码和数量后，才允许加购或立即购买。

如果 AI 返回 `skuId`：

- 前端可以默认选中该 SKU。
- 仍然展示 SKU 和数量确认。
- 用户可以更换颜色或尺码。

## 状态管理设计

建议按业务域拆状态：

- `authStore`：token、当前用户、登录状态。
- `assistantStore`：当前 threadId、消息列表、流式状态、推荐引用。
- `cartStore`：购物车列表、数量摘要、金额摘要。
- `commerceActionStore`：当前待确认动作。
- `uiStore`：购物车抽屉、确认弹窗、移动端面板开关。

服务端数据建议由 Query 层缓存：

- 商品列表。
- 商品详情。
- 购物车列表。
- 订单列表。
- 订单详情。

本地 UI 状态和服务端数据不要混在同一个 store 里。

## 错误处理

统一错误展示：

- `401 unauthorized`：跳转登录页或触发 token refresh。
- `validation_failed`：展示字段级提示。
- `bad_request`：展示业务错误，例如库存不足、订单状态不允许。
- `not_found`：展示资源不存在，例如商品或订单不存在。
- `external_service_error`：AI 服务不可用，提示用户稍后重试。

交易动作失败处理：

- 加购失败：保留确认面板，展示失败原因。
- 立即购买失败：保留 SKU 选择和数量，提示库存或商品状态问题。
- 购物车结算失败：刷新购物车和商品状态。
- mock 支付失败：刷新订单详情，展示当前订单状态。

AI 流式失败处理：

- 已显示的 token 保留。
- 当前 assistant 消息标记为失败。
- 提供“重试”按钮。
- 可降级调用同步聊天接口。

## 第一版范围

第一版实现：

- 前端工程初始化。
- 目标 monorepo 目录结构迁移。
- 登录、注册、token 管理。
- AI 推荐页。
- 传统浏览页。
- AI 流式聊天。
- 商品卡片和推荐卡片。
- SKU 选择。
- 用户确认后加入购物车。
- 用户确认后立即购买。
- 购物车页和购物车抽屉。
- 购物车结算。
- 订单列表和订单详情。
- mock 支付。

第一版不实现：

- 真实支付。
- 退款和售后。
- AI 自动下单。
- 多 SKU 套装一键购买。
- 地址簿和收货地址快照。
- 优惠券、运费、发票。
- 管理后台。
- 移动端 App。

## 开发顺序建议

推荐拆成以下阶段：

1. 目录重组：把当前 Java 后端移动到 `backend/`，建立根级 README 和全栈目录结构。
2. 前端工程初始化：创建 `frontend/`，配置 Vite、TypeScript、路由、基础样式和 API 客户端。
3. Auth：登录、注册、token 保存、受保护路由。
4. Catalog：商品浏览页、商品卡片、商品详情和 SKU 选择。
5. Cart：购物车接口、购物车抽屉、购物车页。
6. Order：购物车结算、立即购买、订单详情。
7. Assistant：AI 推荐页、流式消息、会话列表。
8. Recommendation：AI 推荐引用转商品卡片。
9. Commerce Action：确认层打通加购、立即购买、购物车结算和 mock 支付。
10. Traditional Browse Assistant：传统浏览页右侧 AI 辅助。
11. UI polish：响应式、加载态、空状态、错误态。
12. Verification：前端构建、关键流程手动验证、后端 `verify` 保持通过。

## 验收标准

功能验收：

- 用户可以注册、登录并进入前端。
- 用户可以在 AI 推荐页发起对话，并看到流式回复。
- AI 推荐结果可以转成商品卡片展示。
- 用户点击推荐卡片后，必须确认才能加入购物车。
- 用户点击推荐卡片后，必须确认才能立即购买。
- 用户可以在传统浏览页搜索商品。
- 用户可以在传统浏览页打开 AI 辅助并询问当前商品。
- 用户可以查看购物车、修改数量、删除商品。
- 用户可以从购物车创建订单。
- 用户可以查看订单详情并执行 mock 支付。

工程验收：

- 后端位于 `backend/`。
- 前端位于 `frontend/`。
- 根目录保留 `docs/`、`docker-compose.yml`、`.github/` 和 `README.md`。
- 后端迁移后 `cd backend; .\mvnw.cmd verify` 通过。
- 前端 `npm run build` 通过。
- 根级 README 说明前后端启动方式。

## 审批点

开始开发前需要确认：

- 保持当前项目名称 `Intelligent Outfit Recommendation System` 不变，只把内部结构调整为 `backend/` 和 `frontend/`。
- 第一版前端技术栈采用 React、TypeScript、Vite。
- 第一版主入口是 AI 推荐页，传统浏览页作为第二入口。
- 所有加购、立即购买、结算和支付动作都必须经过用户确认。
- 第一版不新增 Java 后端接口，只消费已有后端能力。
