# User Profile And Preference Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let logged-in users maintain the profile, body data, and shopping preferences already consumed by Java's assistant context.

**Architecture:** Keep Java as the owner of user profile facts. The React frontend only reads and updates `/api/me/profile`, `/api/me/body-data`, and `/api/me/preferences`; the next assistant request naturally picks up saved data through existing Java context assembly.

**Tech Stack:** React 18, TypeScript, Vite, Vitest, existing Spring Boot user profile APIs.

## Global Constraints

- Do not add a separate frontend-owned user profile store.
- Do not let the frontend forge assistant `user_context`.
- Do not change Java-Python assistant contracts in this stage.
- Keep auth/account security fields out of the profile-preference page.
- Treat `/api/me/*` as current-user APIs; do not add userId route parameters.

---

## Files

- Create: `frontend/src/pages/ProfilePreferencesPage.tsx`
- Create: `frontend/src/pages/ProfilePreferencesPage.test.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/shared/api/client.test.ts`
- Modify: `frontend/src/shared/api/types.ts`
- Modify: `frontend/src/styles.css`

## Task 1: Frontend API Boundary

**Interfaces:**
- Consumes: Java `/api/me/profile`, `/api/me/body-data`, `/api/me/preferences`
- Produces: typed current-user profile, body data, and preferences requests

- [x] **Step 1: Write failing API client test**

Expected behavior:

- Preference updates call `/api/me/preferences`.
- Request payload keeps style, color, category, and budget fields structured.

- [x] **Step 2: Add TypeScript API types and client methods**

Added `UserProfile*`, `UserBodyData*`, and `UserPreferences*` types plus `profile`, `updateProfile`, `bodyData`, `updateBodyData`, `preferences`, and `updatePreferences` client methods.

## Task 2: Profile Preference Page

**Interfaces:**
- Consumes: typed API methods from `shared/api/client`
- Produces: compact forms with loading, save, success, and validation states

- [x] **Step 1: Write failing page tests**

Expected behavior:

- Page loads all three profile data groups.
- Profile and body data saves call the correct client methods.
- Preference list inputs are normalized before saving.
- Invalid budget ranges are blocked before calling the API.

- [x] **Step 2: Implement page**

Added `ProfilePreferencesPage` with separate sections for:

- 基础资料: nickname, gender, birthday, avatar URL.
- 身体数据: height, weight, gender, preferred fit, shoulder, bust, waist, hip.
- 穿衣偏好: preferred styles, preferred colors, disliked colors, preferred categories, budget min, budget max.

Preference list inputs accept comma, Chinese comma, semicolon, Chinese semicolon, or newline separators and are normalized to de-duplicated arrays before saving.

## Task 3: Navigation And Styling

- [x] **Step 1: Add top navigation entry**

Added "我的偏好" to the existing in-app view switcher without introducing a new routing layer.

- [x] **Step 2: Add responsive page styles**

Added restrained form layout styles that match the existing workbench UI and collapse to one column on small screens.

## Task 4: Verification

- [x] **Step 1: Run focused frontend tests**

```bash
cd frontend
npm test -- --run ProfilePreferencesPage client.test.ts
```

- [x] **Step 2: Run full frontend tests**

```bash
cd frontend
npm test -- --run
```

- [x] **Step 3: Run frontend build**

```bash
cd frontend
npm run build
```

- [x] **Step 4: Run backend user profile tests**

```bash
cd backend
sh ./mvnw -q -Dtest=UserProfileControllerTests,UserProfileServiceTests test
```

## Completion Criteria

- User can view and save profile basics.
- User can view and save body data used for sizing.
- User can view and save style, color, category, and budget preferences.
- Saved data remains Java-owned and is used by the next assistant context build.
- UI handles loading, success, validation errors, and load failures.
- No MQ, address book, admin profile management, or assistant contract change is introduced.
