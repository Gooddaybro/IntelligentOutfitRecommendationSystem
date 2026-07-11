# NOIR.AI Frontend Visual Evolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the existing commerce frontend into a coherent, accessible NOIR.AI dark future-luxury experience while preserving every authentication, chat, recommendation, commerce, cart, and order API flow.

**Architecture:** Keep the existing React state hooks, API clients, and commerce action boundaries unchanged. Add a presentation-only application shell state for the one-time authenticated entrance animation, reorganize `AiShoppingPage` into the selected left-chat/right-editorial-stage layout, and extend the existing `ProductCard` with visual variants rather than duplicating product action logic. Apply a semantic CSS-token theme across all views and existing overlays.

**Tech Stack:** React 18, TypeScript 5.6, Vite 6, CSS custom properties, lucide-react, Vitest + Testing Library, Playwright.

## Global Constraints

- Modify only `IntelligentOutfitRecommendationSystem/frontend`; do not change Java, Python, API clients, shared contracts, backend response fields, or request timing.
- Retain the existing login, streaming chat, fallback chat, recommendation, behavior-event, add-to-cart, buy-now, cart, checkout, payment, orders, and profile flows.
- Use no new dependencies, UI libraries, external image services, Canvas, WebGL, video assets, or remote fonts.
- Use real backend product images and facts only; missing images, match scores, or recommendation reasons must render an honest fallback rather than fabricated content.
- Use `NOIR.AI` as the visual brand and retain Chinese task labels/content for operational clarity.
- Use only semantic CSS tokens for the new palette: base `#080A0F`, elevated `#10131C`, primary text `#F5F7FF`, secondary text `#9AA3B8`, AI accent `#7785FF`, cyan highlight `#7BE7E0`, and price `#F2B56B`.
- Default motion must be 900ms or less, animate only `opacity`/`transform` where practical, and fully disable new motion in `prefers-reduced-motion: reduce`.
- Maintain keyboard focus visibility, readable contrast, accessible names for icon-only controls, and a usable single-column layout at 760px and below.
- Preserve existing test IDs; add new IDs or data attributes only where the plan names them.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `frontend/src/app/App.tsx` | Authenticated NOIR application shell, navigation presentation, and one-time entrance state; remains the owner of existing routing and domain hooks. |
| `frontend/src/pages/AiShoppingPage.tsx` | Direct AI workbench layout and the featured/supporting/remaining recommendation composition; retains candidate loading and behavior-event plumbing. |
| `frontend/src/features/catalog/ProductCard.tsx` | Reusable semantic visual variants for standard, featured, and supporting product cards while keeping the existing commerce event contract. |
| `frontend/src/features/catalog/ProductCard.test.tsx` | Focused unit coverage for variant semantics and unchanged commerce actions. |
| `frontend/src/features/assistant/ChatPanel.tsx` | Existing streaming interaction with presentation-only workbench landmarks and unchanged request behavior. |
| `frontend/src/features/auth/AuthPanel.tsx` | NOIR brand copy and login form landmarks; form behavior remains intact. |
| `frontend/src/pages/ProductBrowsePage.tsx` | Deep-linkable page heading/landmark selectors for the themed catalog view. |
| `frontend/src/pages/CartPage.tsx` | Deep-linkable page heading/landmark selectors for the themed cart view. |
| `frontend/src/pages/OrdersPage.tsx` | Deep-linkable page heading/landmark selectors for the themed order view. |
| `frontend/src/pages/ProfilePreferencesPage.tsx` | Deep-linkable page heading/landmark selector for the themed profile view. |
| `frontend/src/styles.css` | Global token system, components, page layouts, animation, responsive behavior, and reduced-motion overrides. |
| `frontend/e2e/ai-shopping.spec.ts` | End-to-end proof that the NOIR shell and AI editorial stage coexist with the existing commerce flow. |

## Interfaces

```ts
// frontend/src/features/catalog/ProductCard.tsx
export type ProductCardVariant = "standard" | "featured" | "supporting";

type ProductCardProps = {
  candidate: RecommendationCandidate;
  onAction: (action: PendingCommerceAction) => void;
  actionMetadata?: CommerceActionMetadata;
  position?: number;
  onBehaviorEvent?: (event: ProductBehaviorEvent) => void;
  variant?: ProductCardVariant;
};
```

No API, state-hook, request, callback, or domain-model type changes are permitted.

### Task 1: Establish NOIR tokens and the authenticated entrance shell

**Files:**
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/e2e/ai-shopping.spec.ts`

**Consumes:** `useAuthSession`, `useCartState`, `useAssistantShoppingState`, and the existing navigation state in `App`.

**Produces:** `data-testid="app-shell"`, `data-testid="app-topbar"`, and the `is-entered` class that later page-level CSS animations use. The class is purely presentational and never gates interaction.

- [ ] **Step 1: Add the failing shell assertions to the existing Playwright flow.**

  Insert immediately after the existing `nav-ai` visibility assertion in `frontend/e2e/ai-shopping.spec.ts`:

  ```ts
  await expect(page.getByTestId("app-shell")).toHaveClass(/is-entered/);
  await expect(page.getByTestId("app-topbar")).toContainText("NOIR.AI");
  ```

- [ ] **Step 2: Run the focused end-to-end test and verify it fails because the two test IDs and NOIR brand do not yet exist.**

  Run:

  ```bash
  cd frontend && npm run test:e2e -- e2e/ai-shopping.spec.ts
  ```

  Expected: FAIL at `getByTestId("app-shell")` or the topbar brand assertion.

- [ ] **Step 3: Add a one-time presentation-only entrance state and stable shell landmarks.**

  In `frontend/src/app/App.tsx`, change the React import. Add the effect immediately after the existing `const auth = useAuthSession(...)` call, because that hook result is the effect dependency:

  ```tsx
  import { useCallback, useEffect, useState } from "react";

  const [isEntered, setIsEntered] = useState(false);

  useEffect(() => {
    if (!auth.user) {
      setIsEntered(false);
      return;
    }

    const frame = window.requestAnimationFrame(() => setIsEntered(true));
    return () => window.cancelAnimationFrame(frame);
  }, [auth.user]);
  ```

  Replace the authenticated outer markup and brand/topbar opening tags with:

  ```tsx
  <div className={`app-shell${isEntered ? " is-entered" : ""}`} data-testid="app-shell">
    <header className="topbar" data-testid="app-topbar">
      <div className="brand-lockup">
        <Layers3 size={26} />
        <div>
          <p className="brand-title">NOIR<span>.AI</span></p>
          <p className="eyebrow">智能穿搭工作台</p>
        </div>
      </div>
  ```

  Do not change any view button test IDs, view values, hook calls, callback wiring, or confirmation-dialog props.

- [ ] **Step 4: Replace the global palette and add the entrance/reduced-motion rules.**

  At the top of `frontend/src/styles.css`, replace the existing `:root` token values with these semantic values and retain the existing font stack, `font-synthesis`, line-height, and text rendering declarations:

  ```css
  :root {
    --bg: #080a0f;
    --surface: #10131c;
    --surface-soft: #151a26;
    --surface-raised: rgb(20 25 38 / 82%);
    --text: #f5f7ff;
    --muted: #9aa3b8;
    --primary: #7785ff;
    --ai: #7be7e0;
    --ai-dark: #5bc9c2;
    --price: #f2b56b;
    --border: rgb(203 213 255 / 14%);
    --border-strong: rgb(203 213 255 / 25%);
    --shadow: 0 24px 70px rgb(0 0 0 / 34%);
    --shadow-soft: 0 14px 38px rgb(0 0 0 / 24%);
  }

  body {
    background:
      radial-gradient(circle at 14% -8%, rgb(119 133 255 / 22%), transparent 34%),
      radial-gradient(circle at 88% 2%, rgb(123 231 224 / 12%), transparent 26%),
      var(--bg);
    color: var(--text);
  }
  ```

  Append these animation rules before the responsive media queries:

  ```css
  @keyframes noir-rise {
    from { opacity: 0; transform: translateY(14px); }
    to { opacity: 1; transform: translateY(0); }
  }

  .app-shell.is-entered .topbar { animation: noir-rise 360ms ease-out both; }
  .app-shell.is-entered .workbench-chat-column { animation: noir-rise 520ms 90ms ease-out both; }
  .app-shell.is-entered .recommendation-stage__featured { animation: noir-rise 650ms 180ms ease-out both; }
  .app-shell.is-entered .recommendation-stage__supporting { animation: noir-rise 520ms 280ms ease-out both; }

  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after {
      animation-duration: 0.01ms !important;
      animation-iteration-count: 1 !important;
      scroll-behavior: auto !important;
      transition-duration: 0.01ms !important;
    }
  }
  ```

- [ ] **Step 5: Run the focused test to verify the shell passes.**

  Run:

  ```bash
  cd frontend && npm run test:e2e -- e2e/ai-shopping.spec.ts
  ```

  Expected: the shell assertions pass; later recommendation-layout changes are not yet asserted.

- [ ] **Step 6: Commit only the shell, base theme, and end-to-end assertion.**

  ```bash
  cd ..
  git add frontend/src/app/App.tsx frontend/src/styles.css frontend/e2e/ai-shopping.spec.ts
  git commit -m "feat: add noir application shell"
  ```

### Task 2: Add reusable featured and supporting product-card variants

**Files:**
- Modify: `frontend/src/features/catalog/ProductCard.tsx`
- Create: `frontend/src/features/catalog/ProductCard.test.tsx`
- Modify: `frontend/src/styles.css`

**Consumes:** `RecommendationCandidate`, `PendingCommerceAction`, `CommerceActionMetadata`, and the existing `onBehaviorEvent` callback contract.

**Produces:** `ProductCardVariant`, a defaulted `variant` prop, `data-variant`, and semantic `product-card--*` classes for the AI stage and existing catalog views.

- [ ] **Step 1: Write a focused failing product-card variant test.**

  Create `frontend/src/features/catalog/ProductCard.test.tsx`:

  ```tsx
  import { fireEvent, render, screen } from "@testing-library/react";
  import { describe, expect, it, vi } from "vitest";
  import { ProductCard } from "./ProductCard";

  const candidate = {
    spuId: 1002,
    skuId: 2102,
    spuCode: "JACKET_COMMUTE_001",
    name: "通勤轻薄外套",
    categoryName: "外套",
    salePrice: 299,
    rankScore: 0.92
  };

  describe("ProductCard", () => {
    it("marks an editorial featured card without changing the add-to-cart action", () => {
      const onAction = vi.fn();
      render(<ProductCard candidate={candidate} onAction={onAction} variant="featured" />);

      const card = screen.getByTestId("recommendation-card");
      expect(card).toHaveClass("product-card--featured");
      expect(card).toHaveAttribute("data-variant", "featured");
      expect(screen.getByText("AI 首选")).toBeVisible();

      fireEvent.click(screen.getByTestId("add-to-cart-action"));
      expect(onAction).toHaveBeenCalledWith(expect.objectContaining({
        kind: "ADD_TO_CART",
        skuId: 2102,
        unitPrice: 299
      }));
    });
  });
  ```

- [ ] **Step 2: Run the unit test and verify it fails because `variant` and `AI 首选` are unsupported.**

  Run:

  ```bash
  cd frontend && npm test -- src/features/catalog/ProductCard.test.tsx
  ```

  Expected: TypeScript/test failure that `variant` is not a valid prop.

- [ ] **Step 3: Implement the visual-variant interface without changing commerce behavior.**

  In `frontend/src/features/catalog/ProductCard.tsx`, add the exported union and prop:

  ```tsx
  export type ProductCardVariant = "standard" | "featured" | "supporting";

  type ProductCardProps = {
    candidate: RecommendationCandidate;
    onAction: (action: PendingCommerceAction) => void;
    actionMetadata?: CommerceActionMetadata;
    position?: number;
    onBehaviorEvent?: (event: ProductBehaviorEvent) => void;
    variant?: ProductCardVariant;
  };

  export function ProductCard({
    candidate,
    onAction,
    actionMetadata,
    position,
    onBehaviorEvent,
    variant = "standard"
  }: ProductCardProps) {
  ```

  Replace the article opening tag and add the featured label inside `.product-image`:

  ```tsx
  <article
    className={`product-card product-card--${variant}`}
    data-testid="recommendation-card"
    data-sku-id={candidate.skuId}
    data-variant={variant}
    onClick={() =>
      onBehaviorEvent?.({
        eventType: "RECOMMENDATION_CLICKED",
        candidate,
        metadata: position === undefined ? undefined : { position }
      })
    }
  >
    <div className="product-image">
      {variant === "featured" && <span className="featured-card-label">AI 首选</span>}
      {(candidate.rankScore !== undefined || candidate.recommendationReason) && (
        <span className="ai-match-badge">{matchLabel}</span>
      )}
  ```

  Keep the original `onClick`, add-to-cart click, buy-now click, action metadata, and behavior-event payloads byte-for-byte equivalent to the previous logic.

- [ ] **Step 4: Add variant sizing and label styles to `frontend/src/styles.css`.**

  Add these rules beside the existing product-card rules; existing catalog cards stay `standard` by default:

  ```css
  .product-card {
    background: linear-gradient(150deg, rgb(24 29 42 / 96%), rgb(12 15 23 / 96%));
    border-color: var(--border);
    box-shadow: var(--shadow-soft);
  }

  .product-card--featured { min-height: 100%; }
  .product-card--supporting .product-body { padding: 14px; }
  .product-card--supporting .product-body h3 { font-size: 15px; }
  .product-card--supporting .product-actions { grid-template-columns: 1fr; }

  .featured-card-label {
    background: linear-gradient(100deg, var(--primary), #a084ff);
    border-radius: 999px;
    color: #fff;
    font-size: 11px;
    font-weight: 800;
    left: 14px;
    letter-spacing: 0.08em;
    padding: 6px 9px;
    position: absolute;
    text-transform: uppercase;
    top: 14px;
    z-index: 2;
  }
  ```

- [ ] **Step 5: Run the new unit test and the existing commerce tests.**

  Run:

  ```bash
  cd frontend && npm test -- src/features/catalog/ProductCard.test.tsx src/features/commerce-action/commerceActions.test.ts
  ```

  Expected: PASS; the test confirms only appearance semantics changed while the action uses the backend SKU price.

- [ ] **Step 6: Commit the reusable product-card change.**

  ```bash
  cd ..
  git add frontend/src/features/catalog/ProductCard.tsx frontend/src/features/catalog/ProductCard.test.tsx frontend/src/styles.css
  git commit -m "feat: add editorial product card variants"
  ```

### Task 3: Replace the AI hero with the selected editorial recommendation stage

**Files:**
- Modify: `frontend/src/pages/AiShoppingPage.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/e2e/ai-shopping.spec.ts`

**Consumes:** Existing `recommendations`, `recommendationMeta`, `isRecommendationsLoading`, `chatState`, `onAction`, `onRefreshCart`, and behavior-event functions.

**Produces:** `data-testid="ai-workbench"`, `data-layout="editorial-stage"`, one featured product card, up to two supporting cards, an honest loading skeleton/empty state, and remaining cards without changing their order or callbacks.

- [ ] **Step 1: Add failing editorial-stage assertions to the Playwright flow.**

  Insert after the current first recommendation-card assertion in `frontend/e2e/ai-shopping.spec.ts`:

  ```ts
  await expect(page.getByTestId("ai-workbench")).toHaveAttribute("data-layout", "editorial-stage");
  await expect(page.getByTestId("recommendation-card").first()).toHaveAttribute("data-variant", "featured");
  await expect(page.getByTestId("recommendation-card").first()).toContainText("AI 首选");
  ```

- [ ] **Step 2: Run the end-to-end test and verify the new stage assertions fail.**

  Run:

  ```bash
  cd frontend && npm run test:e2e -- e2e/ai-shopping.spec.ts
  ```

  Expected: FAIL because the old page has `outfit-workbench`, a hero section, and no stage data attribute or featured card.

- [ ] **Step 3: Remove the decorative hero and build the direct workbench composition.**

  In `frontend/src/pages/AiShoppingPage.tsx`:

  1. Remove the `Sparkles` import, `quickPrompts`, `cartTotal`, `useHeroPrompt`, and the Hero `<section>`.
  2. Remove the now-unused `cartItems` and `onOpenCart` props from `AiShoppingPageProps`, the component parameter list, and the `App` call site. `CartDrawer` remains rendered by `App`, so cart access is preserved.
  3. Replace the current `<main>` content with this structure, retaining the existing recommendation event callback body exactly as written:

  ```tsx
  <main className="workbench outfit-workbench noir-workbench" data-testid="ai-workbench" data-layout="editorial-stage">
    <section className="workbench-chat-column">
      <ChatPanel
        onRecommendations={(items, meta) => {
          setRecommendations(items);
          setRecommendationMeta(meta);
        }}
        state={chatState}
      />
    </section>

    <section className="recommendation-stage" data-testid="recommendation-panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">CURATED / AI</p>
          <h2>为你策展的单品</h2>
        </div>
        <span>{isRecommendationsLoading ? "正在筛选" : `${recommendations.length} 件`}</span>
      </div>
      {recommendationMeta?.hasAiResult && !recommendationMeta.hasStrongMatch && (
        <p className="recommendation-notice">AI 暂时没有选出强匹配商品，你可以继续浏览当前候选。</p>
      )}
      {isRecommendationsLoading && <div className="recommendation-stage__skeleton" aria-label="推荐商品加载中" />}
      {!isRecommendationsLoading && recommendations.length === 0 && (
        <p className="recommendation-stage__empty">告诉 AI 你的场景、风格或预算，专属推荐会在这里出现。</p>
      )}
      {recommendations.length > 0 && (
        <div className="recommendation-stage__grid">
          <div className="recommendation-stage__featured">
            <ProductCard candidate={recommendations[0]} variant="featured" onAction={onAction} actionMetadata={actionMetadata} position={1} onBehaviorEvent={recordEvent} />
          </div>
          <div className="recommendation-stage__supporting">
            {recommendations.slice(1, 3).map((candidate, index) => (
              <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} variant="supporting" onAction={onAction} actionMetadata={actionMetadata} position={index + 2} onBehaviorEvent={recordEvent} />
            ))}
          </div>
        </div>
      )}
      {recommendations.length > 3 && (
        <div className="product-grid recommendation-stage__remaining">
          {recommendations.slice(3).map((candidate, index) => (
            <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} onAction={onAction} actionMetadata={actionMetadata} position={index + 4} onBehaviorEvent={recordEvent} />
          ))}
        </div>
      )}
    </section>
  </main>
  ```

  Define the two local values immediately before `return` so the JSX is readable and all existing metadata remains unchanged:

  ```tsx
  const actionMetadata = recommendationMeta?.hasAiResult
    ? { source: "ASSISTANT_RECOMMENDATION" as const, threadId: chatState.threadId }
    : undefined;
  const recordEvent = (event: { eventType: BehaviorEventType; candidate: RecommendationCandidate; metadata?: Record<string, unknown> }) =>
    recordRecommendationEvent(event.eventType, event.candidate, event.metadata);
  ```

- [ ] **Step 4: Add the desktop, loading, and compact-stage CSS.**

  Replace the old `.outfit-workbench`, `.stylist-hero`, `.hero-*`, and three-column `.ai-layout` rules with:

  ```css
  .noir-workbench {
    display: grid;
    gap: 20px;
    grid-template-columns: minmax(300px, 0.78fr) minmax(0, 1.72fr);
    min-height: calc(100vh - 132px);
    padding: 20px;
  }

  .workbench-chat-column,
  .recommendation-stage {
    background: var(--surface-raised);
    backdrop-filter: blur(20px);
    border: 1px solid var(--border);
    border-radius: 24px;
    min-width: 0;
  }

  .workbench-chat-column { padding: 18px; }
  .recommendation-stage { padding: 22px; }
  .recommendation-stage__grid { display: grid; gap: 16px; grid-template-columns: minmax(0, 1.35fr) minmax(210px, 0.78fr); }
  .recommendation-stage__supporting { display: grid; gap: 16px; }
  .recommendation-stage__remaining { margin-top: 16px; }
  .recommendation-stage__skeleton { animation: noir-pulse 900ms ease-in-out infinite alternate; background: linear-gradient(120deg, #1d2535, #2a3652, #1d2535); border-radius: 18px; min-height: 460px; }
  .recommendation-stage__empty { align-items: center; border: 1px dashed var(--border-strong); border-radius: 18px; color: var(--muted); display: flex; justify-content: center; min-height: 300px; padding: 24px; text-align: center; }

  @keyframes noir-pulse { from { opacity: 0.48; } to { opacity: 0.9; } }
  ```

- [ ] **Step 5: Add the responsive stage rules without changing the existing 760px breakpoint.**

  Add before the current `@media (max-width: 760px)` block:

  ```css
  @media (max-width: 1100px) {
    .noir-workbench { grid-template-columns: 1fr; }
  }

  @media (max-width: 760px) {
    .noir-workbench { gap: 14px; min-height: auto; padding: 12px; }
    .recommendation-stage__grid { grid-template-columns: 1fr; }
    .recommendation-stage__supporting { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  }

  @media (max-width: 520px) {
    .recommendation-stage__supporting { grid-template-columns: 1fr; }
  }
  ```

- [ ] **Step 6: Run the edited end-to-end test and verify the original commerce assertions still pass.**

  Run:

  ```bash
  cd frontend && npm run test:e2e -- e2e/ai-shopping.spec.ts
  ```

  Expected: PASS; the first real recommendation is featured, the same behavior events are recorded, and confirmation still precedes cart and payment actions.

- [ ] **Step 7: Commit only the AI composition and its end-to-end coverage.**

  ```bash
  cd ..
  git add frontend/src/pages/AiShoppingPage.tsx frontend/src/app/App.tsx frontend/src/styles.css frontend/e2e/ai-shopping.spec.ts
  git commit -m "feat: build noir ai recommendation stage"
  ```

### Task 4: Theme the AI conversation and authentication surfaces without changing requests

**Files:**
- Modify: `frontend/src/features/assistant/ChatPanel.tsx`
- Modify: `frontend/src/features/auth/AuthPanel.tsx`
- Modify: `frontend/src/styles.css`

**Consumes:** Existing `ChatPanelState`, `streamAssistantChat`, `api.chat`, and auth callbacks.

**Produces:** Presentation-only class names and labeled conversation landmarks; the chat/filter state and auth submissions remain exactly as they are.

- [ ] **Step 1: Run the existing assistant-state test before presentation edits.**

  The no-fabrication behavior is covered by Task 2: a card renders no match badge or recommendation reason unless the API supplied the field. This task has no state or request change, so it must not add a misleading unit test for CSS class names.

  Run:

  ```bash
  cd frontend && npm test -- src/features/assistant/assistantState.test.ts
  ```

  Expected: PASS before the presentation-only markup change.

- [ ] **Step 2: Add only presentation landmarks.**

  In `frontend/src/features/assistant/ChatPanel.tsx`, retain all submit/request code and adjust only return markup:

  ```tsx
  <section className="chat-panel chat-panel--noir" aria-label="AI 穿搭对话">
    <div className="section-heading chat-panel__heading">
      <div>
        <p className="eyebrow">CONVERSATION / AI</p>
        <h2>当前穿搭线索</h2>
      </div>
    </div>
  ```

  Add `className="ai-insight ai-insight--noir"` to the insight block, `className="message-list message-list--noir"` to the message list, and `className="chat-input chat-input--noir"` to the form. Do not alter any `data-testid`, form submission, error handling, input value, or stop-generation logic.

  In `frontend/src/features/auth/AuthPanel.tsx`, replace only the eyebrow content and add the modifier class:

  ```tsx
  <section className="auth-panel auth-panel--noir">
    <div>
      <p className="eyebrow">NOIR.AI / PERSONAL STYLIST</p>
      <h1>从一段对话开始，找到你的下一套穿搭</h1>
    </div>
  ```

- [ ] **Step 3: Add deep-surface chat and auth styling.**

  Add the following rules to `frontend/src/styles.css`:

  ```css
  .auth-shell { background: radial-gradient(circle at 50% 18%, rgb(119 133 255 / 20%), transparent 34%), var(--bg); }
  .auth-panel--noir, .chat-panel--noir { background: transparent; border: 0; box-shadow: none; }
  .ai-insight--noir { background: rgb(123 231 224 / 8%); border-color: rgb(123 231 224 / 24%); color: var(--text); }
  .ai-insight--noir p, .ai-insight--noir span, .ai-insight--noir li { color: inherit; }
  .message-list--noir { background: rgb(4 6 11 / 48%); border-color: var(--border); }
  .message.assistant { background: rgb(255 255 255 / 7%); border-color: var(--border); color: var(--text); }
  .message.user { background: linear-gradient(115deg, #6878ee, #8d6fff); }
  .chat-input--noir textarea, .filter-row input, .filter-row label { background: rgb(5 7 13 / 52%); border-color: var(--border); color: var(--text); }
  ```

- [ ] **Step 4: Run the assistant test and the existing end-to-end flow.**

  Run:

  ```bash
  cd frontend && npm test -- src/features/assistant/assistantState.test.ts && npm run test:e2e -- e2e/ai-shopping.spec.ts
  ```

  Expected: PASS; typing, filter propagation, streaming, fallback, stop, and authentication use their existing code paths.

- [ ] **Step 5: Commit the visual-only conversation and auth change.**

  ```bash
  cd ..
  git add frontend/src/features/assistant/ChatPanel.tsx frontend/src/features/auth/AuthPanel.tsx frontend/src/styles.css
  git commit -m "feat: style noir conversation and auth surfaces"
  ```

### Task 5: Apply the same NOIR system to catalog, cart, orders, profile, and overlays

**Files:**
- Modify: `frontend/src/pages/ProductBrowsePage.tsx`
- Modify: `frontend/src/pages/CartPage.tsx`
- Modify: `frontend/src/pages/OrdersPage.tsx`
- Modify: `frontend/src/pages/ProfilePreferencesPage.tsx`
- Modify: `frontend/src/features/cart/CartDrawer.tsx`
- Modify: `frontend/src/features/commerce-action/ConfirmActionDialog.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/e2e/ai-shopping.spec.ts`

**Consumes:** Existing page props, API calls, existing test IDs, and existing commerce confirmation boundary.

**Produces:** Page and overlay modifier classes/data landmarks for visual verification; no new navigation, form field, commerce, payment, or profile state.

- [ ] **Step 1: Add failing visual-landmark assertions for cart and orders to the existing flow.**

  In `frontend/e2e/ai-shopping.spec.ts`, add these assertions after the existing cart and order assertions:

  ```ts
  await expect(page.getByTestId("cart-page")).toHaveClass(/noir-page/);
  await expect(page.getByTestId("orders-page")).toHaveClass(/noir-page/);
  await expect(page.getByTestId("confirm-action-dialog")).toHaveClass(/confirm-dialog--noir/);
  ```

  Move the last assertion to immediately after the first assertion that opens the confirmation dialog, before the cancel click.

- [ ] **Step 2: Run the end-to-end test and verify the new page/overlay landmarks fail.**

  Run:

  ```bash
  cd frontend && npm run test:e2e -- e2e/ai-shopping.spec.ts
  ```

  Expected: FAIL because the existing markup has none of the NOIR classes or page test IDs.

- [ ] **Step 3: Add semantic visual modifiers without changing page callbacks.**

  Apply these opening tags while retaining all child markup and existing callbacks:

  ```tsx
  // ProductBrowsePage.tsx
  <main className="workbench browse-layout noir-page noir-page--browse" data-testid="browse-page">

  // CartPage.tsx
  <main className="workbench cart-layout noir-page noir-page--cart" data-testid="cart-page">

  // OrdersPage.tsx
  <main className="workbench cart-layout noir-page noir-page--orders" data-testid="orders-page">

  // ProfilePreferencesPage.tsx
  <main className="workbench profile-layout noir-page noir-page--profile" data-testid="profile-page">

  // ConfirmActionDialog.tsx
  <section className="confirm-dialog confirm-dialog--noir" data-testid="confirm-action-dialog" role="dialog" aria-modal="true" aria-label="确认交易动作">

  // CartDrawer.tsx
  <aside className="floating-cart floating-cart--noir" aria-label="购物车摘要">
  ```

  Keep every current page `data-testid`, API invocation, loading branch, form input, and button callback intact.

- [ ] **Step 4: Apply the shared page, overlay, form, and row styling.**

  Add these selector groups to `frontend/src/styles.css` and replace old hard-coded light backgrounds for the same existing selectors with the listed variables:

  ```css
  .noir-page, .catalog-panel, .profile-section, .confirm-dialog--noir {
    background: var(--surface-raised);
    backdrop-filter: blur(18px);
    border-color: var(--border);
    box-shadow: var(--shadow-soft);
  }

  .browse-summary-row, .cart-row, .order-row, .profile-empty-state {
    background: rgb(8 11 18 / 62%);
    border-color: var(--border);
  }

  .profile-form-grid label, .profile-list-fields label, .cart-main p, .browse-summary-row p, .order-row p { color: var(--muted); }
  .segmented { background: rgb(2 4 9 / 56%); }
  .segmented button.active, .primary-button { background: linear-gradient(110deg, #6776e9, #8d70ff); border-color: transparent; color: #fff; }
  .floating-cart--noir button { background: linear-gradient(110deg, #6776e9, #8d70ff); border-color: transparent; color: #fff; }
  .modal-backdrop { background: rgb(2 4 10 / 72%); backdrop-filter: blur(10px); }
  .summary-list { background: rgb(2 4 10 / 40%); border-color: var(--border); }
  .error-text { color: #ff9d9d; }
  .status-line { background: rgb(123 231 224 / 10%); border-color: rgb(123 231 224 / 28%); color: var(--text); }
  ```

  Ensure product image fallbacks remain visible against dark cards and keep price styling on `var(--price)`.

- [ ] **Step 5: Run profile unit tests and the full existing commerce end-to-end flow.**

  Run:

  ```bash
  cd frontend && npm test -- src/pages/ProfilePreferencesPage.test.tsx && npm run test:e2e -- e2e/ai-shopping.spec.ts
  ```

  Expected: PASS; profile save validation remains unchanged, and cart/order/payment flow continues to work after visual modifiers.

- [ ] **Step 6: Commit only the themed pages and their tests.**

  ```bash
  cd ..
  git add frontend/src/pages/ProductBrowsePage.tsx frontend/src/pages/CartPage.tsx frontend/src/pages/OrdersPage.tsx frontend/src/pages/ProfilePreferencesPage.tsx frontend/src/features/cart/CartDrawer.tsx frontend/src/features/commerce-action/ConfirmActionDialog.tsx frontend/src/styles.css frontend/e2e/ai-shopping.spec.ts
  git commit -m "feat: extend noir theme across commerce pages"
  ```

### Task 6: Verify reduced motion and narrow-screen usability

**Files:**
- Modify: `frontend/e2e/ai-shopping.spec.ts`
- Modify: `frontend/src/styles.css`

**Consumes:** The shell test IDs from Task 1 and stage layout classes from Task 3.

**Produces:** Automated verification that the reduced-motion CSS override is active and that the 760px mobile layout keeps the AI input and featured recommendation reachable.

- [ ] **Step 1: Add a failing reduced-motion and mobile Playwright test.**

  Append this test to `frontend/e2e/ai-shopping.spec.ts`:

  ```ts
  test("NOIR workbench stays usable with reduced motion on a narrow screen", async ({ page }) => {
    await installApiMocks(page);
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.setViewportSize({ width: 390, height: 844 });

    await page.goto("/");
    await page.getByTestId("auth-username").fill("e2e_user");
    await page.getByTestId("auth-password").fill("StrongPassword123!");
    await page.getByTestId("auth-submit").click();

    await expect(page.getByTestId("app-topbar")).toHaveCSS("animation-duration", "0.01ms");
    await expect(page.getByTestId("ai-chat-input")).toBeVisible();
    await expect(page.getByTestId("recommendation-card").first()).toBeVisible();
  });
  ```

- [ ] **Step 2: Run the new test and verify it fails before the CSS timing override is present.**

  Run:

  ```bash
  cd frontend && npm run test:e2e -- e2e/ai-shopping.spec.ts -g "reduced motion"
  ```

  Expected: FAIL on the `animation-duration` CSS assertion if the reduced-motion rule was not correctly applied to the topbar.

- [ ] **Step 3: Make the reduced-motion selector deterministic and complete the compact layout.**

  In the existing `@media (prefers-reduced-motion: reduce)` rule in `frontend/src/styles.css`, add a direct topbar declaration so Playwright can verify it regardless of animation shorthand behavior:

  ```css
  .app-shell.is-entered .topbar,
  .app-shell.is-entered .workbench-chat-column,
  .app-shell.is-entered .recommendation-stage__featured,
  .app-shell.is-entered .recommendation-stage__supporting {
    animation-duration: 0.01ms !important;
    transform: none;
  }
  ```

  In the existing 760px media block, keep the source order after the general `.chat-input` declaration and add:

  ```css
  .workbench-chat-column, .recommendation-stage { border-radius: 18px; padding: 16px; }
  .message-list { height: min(46vh, 380px); }
  .recommendation-stage__featured .product-image { aspect-ratio: 1 / 1; }
  ```

- [ ] **Step 4: Run the reduced-motion test and the complete end-to-end suite.**

  Run:

  ```bash
  cd frontend && npm run test:e2e -- e2e/ai-shopping.spec.ts
  ```

  Expected: PASS; the narrow layout visibly exposes chat input and the featured product while motion is effectively disabled.

- [ ] **Step 5: Commit accessibility and responsive verification.**

  ```bash
  cd ..
  git add frontend/src/styles.css frontend/e2e/ai-shopping.spec.ts
  git commit -m "test: verify noir motion and mobile layout"
  ```

### Task 7: Run the release-quality verification set and visually inspect every view

**Files:**
- Modify: no source files unless a verification failure reveals a scoped defect

**Consumes:** All implementation tasks above.

**Produces:** Verified frontend build, unit suite, end-to-end suite, and a manual visual acceptance record in the task/PR description rather than new production functionality.

- [ ] **Step 1: Run the production build.**

  Run:

  ```bash
  cd frontend && npm run build
  ```

  Expected: exit code 0 with Vite reporting a built `dist` directory.

- [ ] **Step 2: Run the entire unit suite.**

  Run:

  ```bash
  cd frontend && npm test
  ```

  Expected: PASS for API, assistant, commerce, product-card, and profile tests.

- [ ] **Step 3: Run the complete Playwright suite.**

  Run:

  ```bash
  cd frontend && npm run test:e2e
  ```

  Expected: PASS for commerce confirmation/payment flow and reduced-motion/narrow-screen NOIR workbench flow.

- [ ] **Step 4: Manually inspect the authenticated app at desktop and mobile widths.**

  Check each exact condition:

  ```text
  Desktop 1440px: topbar enters first, chat next, featured card then supporting cards; no standalone splash screen.
  Desktop 1440px: AI page is a left-chat/right-editorial-stage layout; current real recommendation is the featured card.
  Desktop 1440px: browse, cart, order, profile, floating cart, and confirmation dialog share the same dark glass system.
  Mobile 390px: navigation, chat input, recommendation card, cart actions, and confirmation buttons are visible without horizontal overflow.
  Reduced motion: no visible staged movement is required for a usable interface.
  Missing image/score/reason: fallback UI is honest and does not fabricate a product fact.
  ```

- [ ] **Step 5: Inspect the final diff before handoff.**

  Run:

  ```bash
  cd ..
  git diff --check HEAD~6..HEAD
  git status --short
  ```

  Expected: no whitespace errors; only intended frontend visual-evolution changes are present. Do not stage or revert unrelated existing worktree modifications.
