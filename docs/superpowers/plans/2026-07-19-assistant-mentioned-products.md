# AI 导购整套结构与对话提及商品 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留整套穿搭角色结构的同时，将左侧回答中可由 Java 候选池核验的真实商品显示为可加购、可购买的商品卡。

**Architecture:** 继续使用 Python 现有 `product_refs`，由 Java 分两阶段生成 `mentionedItems` 与 `recommendedItems`；前者表达“回答提及且商品身份真实”，后者表达“证据完整的 AI 强推荐”。前端只通过 `spuId + skuId` 关联 Java 候选快照，上方显示角色摘要，下方显示绑定商品卡，弱匹配不产生推荐归因。

**Tech Stack:** Java 21、Spring Boot、JUnit 5、React 18、TypeScript、Vitest、Testing Library、CSS、SSE

## Global Constraints

- Java 仍是商品、SKU、价格、库存、购物车和订单事实的唯一来源。
- 不修改 Python `product_refs` 字段集合，也不更新共享 Java–Python 字段清单。
- 前端不得从自然语言商品名猜测 `spuId` 或 `skuId`。
- `mentionedItems` 只进入 Java 面向前端的同步响应和 SSE `done` 事件。
- 弱匹配商品不得显示 AI 推荐理由、排序分或发送推荐归因事件。
- 所有新 Java 顶层类型、核心业务方法和跨服务 DTO 必须写明职责与边界的 Javadoc。
- 保持现有 `STRONG_MATCH`、`WEAK_FALLBACK`、`EMPTY`、`ERROR` 语义。

---

### Task 1: Java 推荐决策保留安全的对话提及商品

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantMentionedItem.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/RecommendationDecision.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/RecommendationDecisionService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RecommendationDecisionServiceTests.java`

**Interfaces:**
- Produces: `AssistantMentionedItem(Long spuId, Long skuId, String outfitRole)`。
- Produces: `RecommendationDecision(String recommendationStatus, List<AssistantRecommendationItem> recommendedItems, List<AssistantMentionedItem> mentionedItems, int discardedReferences)`。

- [ ] **Step 1: 写入失败的决策测试**

扩展弱匹配测试并增加强匹配断言：

```java
assertThat(decision.recommendationStatus()).isEqualTo("WEAK_FALLBACK");
assertThat(decision.recommendedItems()).isEmpty();
assertThat(decision.mentionedItems()).containsExactly(
        new AssistantMentionedItem(1001L, 2001L, "TOP")
);

assertThat(strongDecision.mentionedItems()).containsExactly(
        new AssistantMentionedItem(1001L, 2001L, "TOP")
);
```

另加未知候选和不可售候选测试，断言它们不进入 `mentionedItems`。

- [ ] **Step 2: 运行测试确认缺少新字段而失败**

Run: `cd backend && sh ./mvnw -q -Dtest=RecommendationDecisionServiceTests test`

Expected: FAIL，编译提示 `AssistantMentionedItem` 或 `mentionedItems()` 不存在。

- [ ] **Step 3: 新建带边界注释的 DTO**

```java
/**
 * Java 面向前端暴露的对话提及商品引用。
 *
 * 该类型只证明 Python 提及的 SKU 属于本轮 Java 候选池且当前可购买；它不承载
 * AI 强推荐理由或排序证据，避免弱匹配商品被错误计入推荐归因。
 */
public record AssistantMentionedItem(Long spuId, Long skuId, String outfitRole) {
}
```

- [ ] **Step 4: 实现两阶段判定**

在 `RecommendationDecisionService.decide` 中先验证候选身份、去重和可售状态；通过后加入 `mentionedItems`。只有 `hasValidEvidence(...)` 通过时才加入 `recommendedItems`。缺少或矛盾证据仍增加 `discardedReferences`，但不移除已核验的提及商品。

核心循环保持以下结构：

```java
if (candidate == null || !seen.add(key) || !isPurchasable(candidate)) {
    discarded++;
    continue;
}
mentioned.add(new AssistantMentionedItem(
        ref.spuId(), ref.skuId(), roleResolver.resolve(candidate.getCategoryName())
));
if (!hasValidEvidence(intent, candidate, ref.matchedDimensions())) {
    discarded++;
    continue;
}
accepted.add(toRecommendedItem(ref, candidate));
```

为核心方法补充 Javadoc，说明 `mentionedItems` 与 `recommendedItems` 的安全差异。

- [ ] **Step 5: 运行决策测试确认通过**

Run: `cd backend && sh ./mvnw -q -Dtest=RecommendationDecisionServiceTests test`

Expected: PASS。

- [ ] **Step 6: 提交 Java 决策层**

```bash
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantMentionedItem.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/RecommendationDecision.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/RecommendationDecisionService.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RecommendationDecisionServiceTests.java
git commit -m "feat: 保留可核验的对话提及商品"
```

### Task 2: Java 同步与 SSE 前端契约输出 mentionedItems

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatResponse.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantStreamDoneEvent.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantControllerTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`

**Interfaces:**
- Consumes: `RecommendationDecision.mentionedItems()`。
- Produces: 同步 JSON `mentionedItems`。
- Produces: SSE done JSON `mentioned_items`。

- [ ] **Step 1: 写入失败的接口测试**

同步响应增加：

```java
.andExpect(jsonPath("$.data.mentionedItems[0].spuId").value(1002))
.andExpect(jsonPath("$.data.mentionedItems[0].skuId").value(2101))
```

SSE 完成事件增加：

```java
assertThat(body).contains("\"mentioned_items\"");
assertThat(body).contains("\"skuId\":2101");
```

- [ ] **Step 2: 运行接口测试确认失败**

Run: `cd backend && sh ./mvnw -q -Dtest=AssistantControllerTests,AssistantServiceTests test`

Expected: FAIL，响应中缺少 `mentionedItems` / `mentioned_items`。

- [ ] **Step 3: 扩展 DTO 与服务装配**

在 `AssistantChatResponse` 的 `recommendedItems` 后加入：

```java
List<AssistantMentionedItem> mentionedItems,
```

在 `AssistantStreamDoneEvent` 加入：

```java
@JsonProperty("mentioned_items") List<AssistantMentionedItem> mentionedItems,
```

更新类级和 `@param` Javadoc，明确该字段只表示候选池内的安全提及，不代表强推荐。旧构造器使用 `List.of()` 保持兼容。

`AssistantService` 的同步与流式路径均从同一个 `RecommendationDecision` 读取：

```java
List<AssistantMentionedItem> mentionedItems = decision.mentionedItems();
```

推荐快照持久化仍只接收 `recommendedItems`，不得把 `mentionedItems` 标记为 selected。

- [ ] **Step 4: 运行接口与服务测试确认通过**

Run: `cd backend && sh ./mvnw -q -Dtest=AssistantControllerTests,AssistantServiceTests test`

Expected: PASS。

- [ ] **Step 5: 提交 Java 前端契约**

```bash
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatResponse.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantStreamDoneEvent.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantControllerTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java
git commit -m "feat: 向前端返回对话提及商品"
```

### Task 3: 前端解析并关联 mentionedItems

**Files:**
- Modify: `frontend/src/shared/api/types.ts`
- Modify: `frontend/src/shared/api/assistantStream.ts`
- Test: `frontend/src/shared/api/assistantStream.test.ts`
- Modify: `frontend/src/features/assistant/ChatPanel.tsx`

**Interfaces:**
- Produces: `MentionedItem { spuId: number; skuId: number; outfitRole?: OutfitRole }`。
- Produces: `RecommendationResultMeta.mentionedItems?: MentionedItem[]`。

- [ ] **Step 1: 写入 SSE 失败测试**

在 done 事件夹具中加入：

```json
"mentioned_items":[{"spuId":1001,"skuId":2001,"outfitRole":"TOP"}]
```

期望解析结果包含：

```ts
mentionedItems: [{ spuId: 1001, skuId: 2001, outfitRole: "TOP" }]
```

另加 camelCase 输入兼容测试。

- [ ] **Step 2: 运行解析器测试确认失败**

Run: `cd frontend && npm exec vitest -- --environment jsdom src/shared/api/assistantStream.test.ts --run`

Expected: FAIL，结果缺少 `mentionedItems`。

- [ ] **Step 3: 实现类型与归一化**

```ts
export type MentionedItem = {
  spuId: number;
  skuId: number;
  outfitRole?: OutfitRole;
};
```

新增 `normalizeMentionedItems(payload)`，兼容 `mentionedItems` / `mentioned_items` 以及内部 `spuId` / `spu_id`、`skuId` / `sku_id`。`done` 事件、同步 `AssistantChatResponse` 和 `RecommendationResultMeta` 均携带该数组。

- [ ] **Step 4: ChatPanel 将提及项关联到候选快照**

`updateRecommendations` 增加 `mentionedItems` 参数，并在 SSE 与同步回退调用处传入。候选角色按以下优先级附加：

```ts
const matched = recommendedItems.find(exactSku)
  ?? recommendedItems.find(sameSpu)
  ?? mentionedItems.find(exactSku);
```

`onRecommendations` 的 meta 同时保存 `mentionedItems`。旧请求序号判断保持不变。

- [ ] **Step 5: 运行解析器和前端类型检查**

Run: `cd frontend && npm exec vitest -- --environment jsdom src/shared/api/assistantStream.test.ts --run && npm run build`

Expected: PASS，TypeScript 构建成功。

- [ ] **Step 6: 提交前端数据链路**

```bash
git add frontend/src/shared/api/types.ts frontend/src/shared/api/assistantStream.ts frontend/src/shared/api/assistantStream.test.ts frontend/src/features/assistant/ChatPanel.tsx
git commit -m "feat: 解析并关联对话提及商品"
```

### Task 4: 右侧展示整套摘要与绑定商品卡

**Files:**
- Modify: `frontend/src/pages/AiShoppingPage.tsx`
- Test: `frontend/src/pages/AiShoppingPage.test.tsx`
- Modify: `frontend/src/features/catalog/ProductCard.tsx`
- Test: `frontend/src/features/catalog/ProductCard.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `RecommendationResultMeta.mentionedItems`。
- Produces: `ProductCard` 新属性 `isMentioned?: boolean`。

- [ ] **Step 1: 写入弱匹配页面失败测试**

构造“24 件候选快照中的 1 件被提及”场景：

```tsx
recommendationMeta={{
  hasAiResult: true,
  hasStrongMatch: false,
  recommendationStatus: "WEAK_FALLBACK",
  resolvedIntent: { requestType: "OUTFIT_ADVICE" },
  recommendedItems: [],
  mentionedItems: [{ spuId: 1, skuId: 2, outfitRole: "TOP" }]
}}
```

断言：

```ts
expect(screen.getByText("对话提及")).toBeVisible();
expect(screen.getByText("真实夏季上衣")).toBeVisible();
expect(screen.getByTestId("add-to-cart-action")).toBeVisible();
expect(screen.getByText("已绑定 1 件 · 候选 24 件")).toBeVisible();
```

增加 `mentionedItems: []` 测试，断言显示“本轮文字建议未绑定真实商品”且没有加购按钮。

- [ ] **Step 2: 运行页面测试确认失败**

Run: `cd frontend && npm exec vitest -- --environment jsdom src/pages/AiShoppingPage.test.tsx --run`

Expected: FAIL，页面仍按角色过滤全部候选。

- [ ] **Step 3: 实现绑定候选选择器与两层布局**

`AiShoppingPage` 使用精确 SKU 关联：

```ts
const mentionedKeys = new Set(
  recommendationMeta?.mentionedItems?.map((item) => `${item.spuId}:${item.skuId}`) ?? []
);
const boundCandidates = recommendations.filter((candidate) =>
  mentionedKeys.has(`${candidate.spuId}:${candidate.skuId}`)
);
const displayCandidates = recommendationMeta?.hasAiResult ? boundCandidates : recommendations;
```

整套请求上方渲染五个紧凑角色摘要；下方“本轮推荐单品”只渲染 `displayCandidates`。绑定数组为空时显示安全提示，不回退到名称匹配。

头部数量使用：

```tsx
已绑定 {boundCandidates.length} 件 · 候选 {recommendations.length} 件
```

- [ ] **Step 4: ProductCard 区分强推荐与对话提及**

```tsx
{shouldShowAiFacts && <span className="ai-match-badge">AI 推荐</span>}
{!shouldShowAiFacts && isMentioned && (
  <span className="conversation-mention-badge">对话提及</span>
)}
```

弱提及卡不显示推荐理由和排序分。`actionMetadataFor` 与 `recordRecommendationEvent` 继续只认可强推荐项，因此弱提及加购不会携带推荐归因。

- [ ] **Step 5: 添加浅色高对比样式**

将 `.outfit-workbench`、`.workbench-chat-column`、`.recommendation-stage`、角色摘要、提示和提及徽标限定为商城浅色米白主题。保持桌面双栏；在现有 900px/640px 断点下改为单列且不产生横向溢出。

- [ ] **Step 6: 运行页面、商品卡和交易动作测试**

Run: `cd frontend && npm exec vitest -- --environment jsdom src/pages/AiShoppingPage.test.tsx src/features/catalog/ProductCard.test.tsx src/features/commerce-action/commerceActions.test.ts --run`

Expected: PASS。

- [ ] **Step 7: 提交前端展示**

```bash
git add frontend/src/pages/AiShoppingPage.tsx frontend/src/pages/AiShoppingPage.test.tsx frontend/src/features/catalog/ProductCard.tsx frontend/src/features/catalog/ProductCard.test.tsx frontend/src/styles.css
git commit -m "feat: 展示整套结构与对话提及商品"
```

### Task 5: 跨服务、回归与视觉验收

**Files:**
- Verify only: `../outfit-project-contract/contracts/java-python-chat/v1.fields.json`

**Interfaces:**
- Verifies: Python `product_refs` 契约不变。
- Verifies: Java 前端契约与前端解析保持一致。

- [ ] **Step 1: 运行共享契约测试**

Run: `cd ../AI-Clothing-Shopping-Assistant-System && ./.venv/bin/python -m unittest tests.test_shared_contract -v`

Run: `cd backend && sh ./mvnw -q -Dtest=SharedJavaPythonContractTests test`

Expected: 两侧均 PASS，字段清单无修改。

- [ ] **Step 2: 运行完整后端验证**

Run: `cd backend && sh ./mvnw verify`

Expected: JUnit 与 Checkstyle 全部通过。

- [ ] **Step 3: 运行完整前端验证**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: 全部 Vitest 测试与 Vite 生产构建通过。

- [ ] **Step 4: 浏览器视觉与交互验收**

用弱匹配场景确认：五角色摘要存在；左侧提及商品在下方显示真实卡片；加购弹出确认并使用正确 SKU；无结构化 ID 时显示未绑定提示；推荐区域为浅色高对比；640px 下无横向溢出；控制台无错误。

- [ ] **Step 5: 检查注释与临时调试内容**

Run: `rg -n "\[DEBUG-|TODO|FIXME" backend/src/main/java frontend/src`

Expected: 没有本次遗留的调试日志或占位注释；新 Java 类型和核心判定方法具备边界说明。

- [ ] **Step 6: 提交验收记录（仅在验证产生必要文档变更时）**

若验证未产生文件变化，不创建空提交。确认 `git status --short` 为空后进入分支完成流程。
