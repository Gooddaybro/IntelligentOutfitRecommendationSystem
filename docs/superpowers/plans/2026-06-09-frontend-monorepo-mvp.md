# Frontend Monorepo MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the current project into `backend/` and `frontend/`, then deliver a first usable React/Vite frontend for AI-guided clothing shopping.

**Architecture:** Keep the existing Java Spring Boot service unchanged in behavior but move it under `backend/`. Add a React TypeScript SPA under `frontend/` that consumes the existing auth, catalog, assistant, cart, order, and mock payment APIs. All commerce actions initiated from AI recommendations pass through an explicit user confirmation boundary before calling backend APIs.

**Tech Stack:** Java 21, Spring Boot, Maven, React, TypeScript, Vite, React Router, CSS Modules/plain CSS, fetch-based API client.

---

### Task 1: Move Java Backend Into `backend/`

**Files:**
- Move: `src/` to `backend/src/`
- Move: `pom.xml` to `backend/pom.xml`
- Move: `mvnw` to `backend/mvnw`
- Move: `mvnw.cmd` to `backend/mvnw.cmd`
- Move: `.mvn/` to `backend/.mvn/`
- Move: `checkstyle.xml` to `backend/checkstyle.xml`
- Move: `HELP.md` to `backend/HELP.md`
- Modify: `.github/workflows/ci.yml`
- Modify: `.gitignore`

- [ ] Move backend-owned files into `backend/`.
- [ ] Update CI backend commands to run with `working-directory: backend`.
- [ ] Update ignore rules for `backend/target/` and `frontend/node_modules/`.
- [ ] Run `cd backend; .\mvnw.cmd verify`.

### Task 2: Create Frontend Shell

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/app/App.tsx`
- Create: `frontend/src/styles.css`

- [ ] Create a Vite React TypeScript project.
- [ ] Add scripts: `dev`, `build`, `preview`, `test`.
- [ ] Add root layout with navigation for AI 推荐、浏览商品、购物车、订单.
- [ ] Run `cd frontend; npm install`.
- [ ] Run `cd frontend; npm run build`.

### Task 3: Add Typed API Client

**Files:**
- Create: `frontend/src/shared/api/client.ts`
- Create: `frontend/src/shared/api/types.ts`
- Create: `frontend/src/shared/api/assistantStream.ts`

- [ ] Implement `ApiError` and `requestJson`.
- [ ] Attach `Authorization: Bearer <token>` when a token exists.
- [ ] Implement typed wrappers for auth, catalog, assistant, cart, order, and payment.
- [ ] Implement `fetch` + `ReadableStream` SSE parsing for `POST /api/assistant/chat/stream`.

### Task 4: Add Auth Module

**Files:**
- Create: `frontend/src/features/auth/authStore.ts`
- Create: `frontend/src/pages/LoginPage.tsx`
- Create: `frontend/src/pages/RegisterPage.tsx`

- [ ] Persist access and refresh tokens in local storage.
- [ ] Add login and register pages.
- [ ] Protect app pages by redirecting unauthenticated users to login.

### Task 5: Add Catalog and Shared Product Components

**Files:**
- Create: `frontend/src/features/catalog/ProductCard.tsx`
- Create: `frontend/src/features/catalog/SkuSelector.tsx`
- Create: `frontend/src/pages/ProductBrowsePage.tsx`

- [ ] Load products from `GET /api/products`.
- [ ] Render searchable product cards.
- [ ] Let the user choose SKU and quantity before commerce actions.
- [ ] Add a right-side AI assistant panel placeholder for browsing context.

### Task 6: Add Commerce Confirmation Layer

**Files:**
- Create: `frontend/src/features/commerce-action/ConfirmActionDialog.tsx`
- Create: `frontend/src/features/commerce-action/commerceActions.ts`

- [ ] Model `ADD_TO_CART`, `BUY_NOW`, `CHECKOUT_CART`, and `MOCK_PAY`.
- [ ] Show product/SKU/quantity details before executing any commerce action.
- [ ] Call the correct backend API only after user confirmation.

### Task 7: Add Cart, Orders, and Payment

**Files:**
- Create: `frontend/src/features/cart/CartDrawer.tsx`
- Create: `frontend/src/pages/CartPage.tsx`
- Create: `frontend/src/pages/OrdersPage.tsx`
- Create: `frontend/src/pages/OrderDetailPage.tsx`

- [ ] Load and mutate cart items.
- [ ] Support cart checkout with `POST /api/orders`.
- [ ] Support order detail and cancellation.
- [ ] Support mock payment through `POST /api/payments/mock-pay`.

### Task 8: Add AI Recommendation Page

**Files:**
- Create: `frontend/src/features/assistant/ChatPanel.tsx`
- Create: `frontend/src/features/recommendation/RecommendationCard.tsx`
- Create: `frontend/src/pages/AiShoppingPage.tsx`

- [ ] Stream assistant messages from `POST /api/assistant/chat/stream`.
- [ ] Show recommended product cards from assistant product references.
- [ ] Route recommendation card actions through `ConfirmActionDialog`.
- [ ] Keep cart summary visible beside the AI conversation.

### Task 9: Update Root Documentation

**Files:**
- Create: `README.md`
- Modify: `docs/superpowers/specs/2026-06-08-ai-clothing-shopping-frontend-design.md`

- [ ] Document backend and frontend startup commands.
- [ ] Document backend verification from `backend/`.
- [ ] Document frontend build verification from `frontend/`.

### Task 10: Final Verification

**Commands:**
- `cd backend; .\mvnw.cmd verify`
- `cd frontend; npm run build`
- `git diff --check`

- [ ] Backend verify passes.
- [ ] Frontend build passes.
- [ ] No whitespace errors.
