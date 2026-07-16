# 水木商城前端 F0–F1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在当前分支完成水木商城第一批可见前端效果，包括可深链路由、用户/管理双壳层、登录后首页、商品策展列表、商品详情和 SKU 决策。

**Architecture:** 使用 React Router 建立 `/app/*` 与 `/admin/*` 路由；现有认证、购物袋、AI 和交易动作仍由 `App` 装配。商品详情通过现有商品详情与推荐候选接口组合真实 SPU/SKU 数据；首期不引入全局状态库，Mock/API 双适配层留到 F2/F3 需要新增后端契约时建立。

**Tech Stack:** React 18、TypeScript、Vite、React Router、Vitest、Testing Library、Playwright、CSS 自定义属性、Lucide React。

---

## 文件结构

```text
frontend/src/
├── app/
│   ├── App.tsx                    顶层认证、交易状态与路由装配
│   ├── CustomerShell.tsx          用户端侧栏、移动导航和内容出口
│   ├── AdminShell.tsx             独立管理台外壳与管理员导航
│   └── navigation.ts              用户端/管理端导航声明
├── pages/
│   ├── HomePage.tsx               登录后商城首页
│   ├── ProductBrowsePage.tsx      分类、搜索、筛选和单一商品网格
│   ├── ProductDetailPage.tsx      商品画廊、SKU 决策与购买动作
│   └── AdminDashboardPage.tsx     第一批后台概览效果
├── features/catalog/
│   ├── ProductCard.tsx            支持进入商品详情
│   ├── skuSelection.ts            纯函数 SKU 组合解析
│   └── skuSelection.test.ts       SKU 解析测试
├── shared/api/
│   ├── client.ts                  复用现有商品接口
│   └── types.ts                   商品详情 SKU 展示类型
└── styles/
    ├── tokens.css                 水木语义令牌
    ├── shell.css                  双壳层和响应式导航
    └── commerce.css               首页、商品列表和详情样式
```

### Task 1：建立可深链路由

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/app/App.tsx`
- Test: `frontend/src/app/AppRoutes.test.tsx`

- [ ] **Step 1：安装 React Router**

Run: `npm install react-router-dom@^7.6.2`

Expected: `package.json` 和 lockfile 出现 `react-router-dom`，命令成功退出。

- [ ] **Step 2：先写失败的路由测试**

```tsx
it("renders a product detail route from a deep link", () => {
  render(<MemoryRouter initialEntries={["/app/products/1001"]}><AppRoutes fixture="authenticated" /></MemoryRouter>);
  expect(screen.getByTestId("product-detail-page")).toBeVisible();
});

it("keeps the admin shell separate", () => {
  render(<MemoryRouter initialEntries={["/admin"]}><AppRoutes fixture="admin" /></MemoryRouter>);
  expect(screen.getByTestId("admin-shell")).toBeVisible();
  expect(screen.queryByTestId("customer-shell")).not.toBeInTheDocument();
});
```

- [ ] **Step 3：运行测试并确认失败**

Run: `npm test -- --run src/app/AppRoutes.test.tsx`

Expected: FAIL，原因是 `AppRoutes` 或目标页面尚不存在。

- [ ] **Step 4：用嵌套路由替换 `ViewKey` 页面切换**

`main.tsx` 在 `BrowserRouter` 内渲染 `App`。`App` 在认证成功后装配如下路由：

```tsx
<Routes>
  <Route path="/app" element={<CustomerShell user={auth.user} cartCount={cart.count} onLogout={logout} />}>
    <Route index element={<Navigate to="home" replace />} />
    <Route path="home" element={<HomePage />} />
    <Route path="ai" element={<AiShoppingPage {...aiProps} />} />
    <Route path="products" element={<ProductBrowsePage onAction={commerce.setPendingAction} />} />
    <Route path="products/:spuId" element={<ProductDetailPage onAction={commerce.setPendingAction} />} />
    <Route path="cart" element={<CartPage items={cart.items} onItemsChange={cart.setItems} onOrderCreated={() => navigate("/app/orders")} />} />
    <Route path="orders" element={<OrdersPage />} />
    <Route path="profile/*" element={<ProfilePreferencesPage />} />
  </Route>
  <Route path="/admin/*" element={<AdminShell user={auth.user} onLogout={logout} />} />
  <Route path="*" element={<Navigate to="/app/home" replace />} />
</Routes>
```

退出登录后清理原有购物袋、AI 和交易动作状态；登录成功默认进入 `/app/home`。

- [ ] **Step 5：运行路由测试**

Run: `npm test -- --run src/app/AppRoutes.test.tsx`

Expected: PASS。

- [ ] **Step 6：中文提交**

```powershell
git add frontend/package.json frontend/package-lock.json frontend/src/main.tsx frontend/src/app frontend/src/pages
git commit -m "前端：建立商城与管理后台路由"
```

### Task 2：实现水木双壳层与响应式导航

**Files:**
- Create: `frontend/src/app/navigation.ts`
- Create: `frontend/src/app/CustomerShell.tsx`
- Create: `frontend/src/app/AdminShell.tsx`
- Create: `frontend/src/styles/tokens.css`
- Create: `frontend/src/styles/shell.css`
- Modify: `frontend/src/main.tsx`
- Test: `frontend/src/app/CustomerShell.test.tsx`

- [ ] **Step 1：先写导航失败测试**

```tsx
it("shows six desktop destinations and five mobile destinations", () => {
  render(<MemoryRouter initialEntries={["/app/home"]}><CustomerShell user={user} cartCount={2} onLogout={vi.fn()} /></MemoryRouter>);
  expect(screen.getByRole("navigation", { name: "商城主导航" })).toBeVisible();
  expect(screen.getByRole("navigation", { name: "移动端主导航" }).querySelectorAll("a")).toHaveLength(5);
  expect(screen.getAllByText(/购物袋/)[0]).toHaveTextContent("2");
});
```

- [ ] **Step 2：运行测试并确认失败**

Run: `npm test -- --run src/app/CustomerShell.test.tsx`

Expected: FAIL，原因是壳层尚不存在。

- [ ] **Step 3：实现声明式导航和壳层**

`navigation.ts` 导出用户端的今日、AI 造型、探索商品、购物袋、订单、个人中心，以及管理端的概览、商品、分类、库存、订单、用户、分析、日志。`CustomerShell` 使用 `NavLink`、`Outlet` 和购物袋数量；`AdminShell` 使用独立导航与 `data-testid="admin-shell"`。

- [ ] **Step 4：建立水木令牌**

```css
:root {
  --forest-950: #193126;
  --forest-900: #263a2d;
  --sage-700: #5f7c58;
  --sage-600: #78936d;
  --moss-100: #dce8d6;
  --linen-50: #f6f1e8;
  --linen-100: #eee6d9;
  --oak-400: #bda98b;
  --ink-900: #26342b;
  --ink-600: #657068;
  --surface: #fffaf2;
  --danger: #a74e43;
  --warning: #a46f2d;
  --radius-sm: 10px;
  --radius-md: 16px;
  --radius-lg: 24px;
}
```

桌面侧栏固定，900px 收窄，640px 以下隐藏侧栏并显示五项底部导航。

- [ ] **Step 5：运行测试和构建**

Run: `npm test -- --run src/app/CustomerShell.test.tsx && npm run build`

Expected: 测试 PASS，构建成功。

- [ ] **Step 6：中文提交**

```powershell
git add frontend/src/app frontend/src/styles frontend/src/main.tsx
git commit -m "前端：实现水木双壳层与响应式导航"
```

### Task 3：实现登录后“我的穿搭空间”首页

**Files:**
- Create: `frontend/src/pages/HomePage.tsx`
- Create: `frontend/src/pages/HomePage.test.tsx`
- Create: `frontend/src/styles/commerce.css`
- Modify: `frontend/src/app/App.tsx`

- [ ] **Step 1：写失败的首页语义测试**

```tsx
it("prioritizes AI, categories and commerce status", () => {
  render(<MemoryRouter><HomePage username="林木" cartCount={2} recommendations={products} /></MemoryRouter>);
  expect(screen.getByRole("heading", { name: /我的穿搭空间/ })).toBeVisible();
  expect(screen.getByRole("link", { name: /和 AI 一起挑选/ })).toHaveAttribute("href", "/app/ai");
  expect(screen.getByRole("link", { name: /探索全部商品/ })).toHaveAttribute("href", "/app/products");
  expect(screen.getByText("购物袋 2 件待决定")).toBeVisible();
});
```

- [ ] **Step 2：确认失败**

Run: `npm test -- --run src/pages/HomePage.test.tsx`

Expected: FAIL，首页组件尚不存在。

- [ ] **Step 3：实现首页**

首页包含问候与 AI 主入口、五个分类快捷入口、真实推荐商品区、购物袋/订单/衣橱画像状态区。无推荐时显示进入 AI 造型师的空状态；不得制造天气和优惠。

- [ ] **Step 4：运行测试**

Run: `npm test -- --run src/pages/HomePage.test.tsx`

Expected: PASS。

- [ ] **Step 5：中文提交**

```powershell
git add frontend/src/pages/HomePage.tsx frontend/src/pages/HomePage.test.tsx frontend/src/styles/commerce.css frontend/src/app/App.tsx
git commit -m "前端：新增我的穿搭空间首页"
```

### Task 4：将探索商品改为单一策展网格

**Files:**
- Modify: `frontend/src/pages/ProductBrowsePage.tsx`
- Create: `frontend/src/pages/ProductBrowsePage.test.tsx`
- Modify: `frontend/src/features/catalog/ProductCard.tsx`
- Modify: `frontend/src/styles/commerce.css`

- [ ] **Step 1：写失败的商品浏览测试**

```tsx
it("shows categories, compact filters and one product grid", async () => {
  render(<MemoryRouter><ProductBrowsePage onAction={vi.fn()} /></MemoryRouter>);
  expect(await screen.findByRole("heading", { name: "探索适合你的穿搭" })).toBeVisible();
  expect(screen.getByRole("button", { name: "外套" })).toBeVisible();
  expect(screen.getByLabelText("商品排序")).toBeVisible();
  expect(screen.getByTestId("catalog-product-grid")).toBeVisible();
  expect(screen.queryByText("候选商品卡片")).not.toBeInTheDocument();
});
```

- [ ] **Step 2：确认失败**

Run: `npm test -- --run src/pages/ProductBrowsePage.test.tsx`

Expected: FAIL，现有页面仍有重复 SPU/SKU 层和聊天侧栏。

- [ ] **Step 3：实现单一网格**

- 删除重复的 SPU 摘要列表和右侧聊天栏。
- 顶部展示搜索、分类快捷入口、筛选 chips、排序和结果数。
- 以推荐候选 SKU 作为当前可交易卡数据；同一 SPU 只展示一个代表卡。
- 商品卡主点击和“查看详情”进入 `/app/products/:spuId`，加购按钮仍保留现有确认动作。
- 搜索失败保留关键词并显示重试。

- [ ] **Step 4：运行页面与商品卡测试**

Run: `npm test -- --run src/pages/ProductBrowsePage.test.tsx src/features/catalog/ProductCard.test.tsx`

Expected: PASS。

- [ ] **Step 5：中文提交**

```powershell
git add frontend/src/pages/ProductBrowsePage.tsx frontend/src/pages/ProductBrowsePage.test.tsx frontend/src/features/catalog/ProductCard.tsx frontend/src/styles/commerce.css
git commit -m "前端：重做探索商品策展网格"
```

### Task 5：实现 SKU 组合解析

**Files:**
- Create: `frontend/src/features/catalog/skuSelection.ts`
- Create: `frontend/src/features/catalog/skuSelection.test.ts`
- Modify: `frontend/src/shared/api/types.ts`

- [ ] **Step 1：写失败的纯函数测试**

```ts
it("selects only the SKU matching color and size", () => {
  expect(resolveSku(skus, { color: "米白", size: "M" })?.skuId).toBe(102);
});

it("returns undefined for an unavailable combination", () => {
  expect(resolveSku(skus, { color: "棕色", size: "XL" })).toBeUndefined();
});

it("reports which options are still missing", () => {
  expect(missingSkuOptions({ color: "米白" })).toEqual(["size"]);
});
```

- [ ] **Step 2：确认失败**

Run: `npm test -- --run src/features/catalog/skuSelection.test.ts`

Expected: FAIL，解析函数不存在。

- [ ] **Step 3：实现最小纯函数**

```ts
export type SkuSelection = { color?: string; size?: string };

export function resolveSku(skus: RecommendationCandidate[], selection: SkuSelection) {
  if (!selection.color || !selection.size) return undefined;
  return skus.find((sku) => sku.color === selection.color && sku.size === selection.size);
}

export function missingSkuOptions(selection: SkuSelection) {
  return (["color", "size"] as const).filter((key) => !selection[key]);
}
```

- [ ] **Step 4：运行测试**

Run: `npm test -- --run src/features/catalog/skuSelection.test.ts`

Expected: PASS。

- [ ] **Step 5：中文提交**

```powershell
git add frontend/src/features/catalog/skuSelection.ts frontend/src/features/catalog/skuSelection.test.ts frontend/src/shared/api/types.ts
git commit -m "前端：增加商品SKU组合解析"
```

### Task 6：实现商品详情与购买决策页

**Files:**
- Create: `frontend/src/pages/ProductDetailPage.tsx`
- Create: `frontend/src/pages/ProductDetailPage.test.tsx`
- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/styles/commerce.css`

- [ ] **Step 1：写失败的详情页测试**

```tsx
it("requires a complete in-stock SKU before purchase", async () => {
  render(<MemoryRouter initialEntries={["/app/products/1001"]}><Routes><Route path="/app/products/:spuId" element={<ProductDetailPage onAction={onAction} />} /></Routes></MemoryRouter>);
  expect(await screen.findByRole("heading", { name: "轻量通勤羽绒服" })).toBeVisible();
  expect(screen.getByRole("button", { name: "加入购物袋" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "米白" }));
  await userEvent.click(screen.getByRole("button", { name: "M" }));
  expect(screen.getByRole("button", { name: "加入购物袋" })).toBeEnabled();
});
```

- [ ] **Step 2：确认失败**

Run: `npm test -- --run src/pages/ProductDetailPage.test.tsx`

Expected: FAIL，详情页不存在。

- [ ] **Step 3：组合现有真实接口**

详情页并行调用 `api.productDetail(spuId)` 和 `api.recommendationCandidates({})`，只保留相同 `spuId` 的 SKU。若没有 SKU，展示“当前商品暂不可购买”；不得用前端生成 SKU、价格或库存。

- [ ] **Step 4：实现详情布局与动作**

- 主图和缩略图占位。
- 商品标题、分类、描述、材质和风格标签。
- 颜色/尺码按钮及不可用组合提示。
- 选中 SKU 后展示真实价格和库存。
- “加入购物袋”“立即购买”复用 `buildAddToCartAction` 和 `buildBuyNowAction`。
- AI 建议明确标注为建议，不生成匹配百分比。

- [ ] **Step 5：运行详情与交易动作测试**

Run: `npm test -- --run src/pages/ProductDetailPage.test.tsx src/features/commerce-action/commerceActions.test.ts`

Expected: PASS。

- [ ] **Step 6：中文提交**

```powershell
git add frontend/src/pages/ProductDetailPage.tsx frontend/src/pages/ProductDetailPage.test.tsx frontend/src/shared/api/client.ts frontend/src/styles/commerce.css
git commit -m "前端：实现商品详情与SKU购买决策"
```

### Task 7：打通 AI 推荐到商品详情

**Files:**
- Modify: `frontend/src/features/catalog/ProductCard.tsx`
- Modify: `frontend/src/pages/AiShoppingPage.tsx`
- Modify: `frontend/src/features/catalog/ProductCard.test.tsx`

- [ ] **Step 1：新增失败测试**

```tsx
it("links an AI recommendation to its product detail", () => {
  render(<MemoryRouter><ProductCard candidate={candidate} onAction={vi.fn()} /></MemoryRouter>);
  expect(screen.getByRole("link", { name: `查看${candidate.name}详情` })).toHaveAttribute("href", `/app/products/${candidate.spuId}`);
});
```

- [ ] **Step 2：确认失败**

Run: `npm test -- --run src/features/catalog/ProductCard.test.tsx`

Expected: FAIL，当前商品卡没有详情链接。

- [ ] **Step 3：实现语义链接并保留行为归因**

使用 `Link` 包裹商品名称/图片和“查看详情”。点击时继续触发 `RECOMMENDATION_CLICKED`，加购和购买按钮阻止冒泡并保持现有动作元数据。

- [ ] **Step 4：运行测试**

Run: `npm test -- --run src/features/catalog/ProductCard.test.tsx src/features/assistant/assistantState.test.ts`

Expected: PASS。

- [ ] **Step 5：中文提交**

```powershell
git add frontend/src/features/catalog/ProductCard.tsx frontend/src/features/catalog/ProductCard.test.tsx frontend/src/pages/AiShoppingPage.tsx
git commit -m "前端：打通AI推荐与商品详情"
```

### Task 8：视觉验证与第一阶段收口

**Files:**
- Modify: `frontend/e2e/ai-shopping.spec.ts`
- Create: `frontend/e2e/customer-commerce.spec.ts`
- Modify: `frontend/src/styles/shell.css`
- Modify: `frontend/src/styles/commerce.css`

- [ ] **Step 1：补充 E2E 断言**

```ts
test("customer can browse from home to a SKU decision", async ({ page }) => {
  await loginAsUser(page);
  await expect(page).toHaveURL(/\/app\/home/);
  await page.getByRole("link", { name: "探索全部商品" }).click();
  await page.getByRole("link", { name: /查看.*详情/ }).first().click();
  await expect(page.getByTestId("product-detail-page")).toBeVisible();
  await expect(page.getByRole("button", { name: "加入购物袋" })).toBeDisabled();
});
```

- [ ] **Step 2：运行完整前端验证**

Run: `npm test -- --run`

Expected: 所有 Vitest 测试 PASS。

Run: `npm run build`

Expected: TypeScript 与 Vite 构建成功。

Run: `npm run test:e2e`

Expected: 所有 Playwright 用例 PASS；如果本机浏览器或后端未启动，记录明确的环境阻塞，不将其误报为功能通过。

- [ ] **Step 3：人工检查三档布局**

在 1440px、900px、390px 检查：侧栏/底栏切换、首页层级、商品网格、详情 SKU 操作、管理台壳层、焦点样式和横向溢出。

- [ ] **Step 4：中文提交**

```powershell
git add frontend/e2e frontend/src/styles
git commit -m "测试：完善水木商城前端首阶段验收"
```

## 计划自检结论

- 规格覆盖：本计划覆盖总设计的 F0 和 F1；结算、订单详情、个人中心和完整管理页将在 F2/F3 独立计划中实施。
- 边界一致：价格、SKU 和库存只来自现有 Java 接口；没有真实 SKU 时禁用购买。
- 类型一致：`ProductDetail` 表示 SPU，`RecommendationCandidate` 表示可交易 SKU，`SkuSelection` 只保存颜色和尺码选择。
- 提交约束：所有新增 Git 提交均使用中文说明，并且只在当前 `learn` 分支工作。
