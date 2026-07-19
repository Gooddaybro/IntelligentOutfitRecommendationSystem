# 衣橱画像浅色主题修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将衣橱画像页面从旧版深色主题迁移到与个人中心一致、文字和表单清晰可辨的浅色米白主题。

**Architecture:** 保留 `ProfilePreferencesPage` 的组件结构、字段、状态和 API 调用，只用一个新的页面级类建立浅色样式作用域。浅色规则放在现有商城样式文件中，避免改动仍服务登录和聊天页面的全局深色变量。

**Tech Stack:** React 18、TypeScript、CSS、Vitest、Testing Library、Vite

## Global Constraints

- 保持现有字段、保存行为、数据转换、接口调用、测试标识和响应式布局不变。
- 不修改用户资料、身体数据或偏好接口。
- 不重构全站旧版深色样式。
- 不改变字段排列、文案或保存交互。

---

### Task 1: 建立浅色页面主题契约并实现样式

**Files:**
- Modify: `frontend/src/pages/ProfilePreferencesPage.test.tsx`
- Modify: `frontend/src/pages/ProfilePreferencesPage.tsx:279`
- Modify: `frontend/src/styles/commerce.css`

**Interfaces:**
- Consumes: `ProfilePreferencesPage` 现有的 `data-testid="profile-page"` 测试契约。
- Produces: 根节点类 `profile-wardrobe-page`，作为商城浅色主题的唯一 CSS 作用域。

- [ ] **Step 1: 写入失败的主题契约测试**

在 `ProfilePreferencesPage.test.tsx` 的加载测试中加入：

```tsx
const page = screen.getByTestId("profile-page");
expect(page).toHaveClass("profile-wardrobe-page");
expect(page).not.toHaveClass("noir-page");
```

- [ ] **Step 2: 运行测试并确认按预期失败**

Run: `npm test -- --run src/pages/ProfilePreferencesPage.test.tsx`

Expected: FAIL，提示 `profile-page` 不包含 `profile-wardrobe-page`，并仍包含 `noir-page`。

- [ ] **Step 3: 替换页面主题类**

将 `ProfilePreferencesPage.tsx` 根节点改为：

```tsx
<main className="profile-layout profile-wardrobe-page" data-testid="profile-page">
```

- [ ] **Step 4: 添加页面级浅色样式**

在 `frontend/src/styles/commerce.css` 的个人中心样式区域加入以下作用域规则：

```css
.profile-wardrobe-page {
  color: var(--ink-900);
  background: var(--linen-50);
  border: 1px solid #d6cbb9;
  border-radius: 18px;
  box-shadow: none;
}
.profile-wardrobe-page .profile-page-heading h1,
.profile-wardrobe-page .profile-section h2 { color: var(--forest-950); }
.profile-wardrobe-page .eyebrow { color: var(--sage-700); }
.profile-wardrobe-page .profile-section {
  background: var(--surface);
  border-color: #d6cbb9;
  box-shadow: 0 12px 28px rgb(38 58 45 / 10%);
  backdrop-filter: none;
}
.profile-wardrobe-page .profile-form-grid label,
.profile-wardrobe-page .profile-list-fields label,
.profile-wardrobe-page .profile-form-grid label span,
.profile-wardrobe-page .profile-list-fields label span { color: var(--ink-600); }
.profile-wardrobe-page input,
.profile-wardrobe-page select,
.profile-wardrobe-page textarea,
.profile-wardrobe-page select[data-native-dark-control="true"],
.profile-wardrobe-page input[type="date"][data-native-dark-control="true"] {
  color-scheme: light;
  color: var(--ink-900);
  background: #fff;
  border-color: #cfc4b2;
  caret-color: var(--ink-900);
}
.profile-wardrobe-page input:focus,
.profile-wardrobe-page select:focus,
.profile-wardrobe-page textarea:focus {
  border-color: var(--sage-700);
  box-shadow: 0 0 0 3px rgb(95 124 88 / 18%);
  outline: none;
}
.profile-wardrobe-page select[data-native-dark-control="true"] option {
  color: var(--ink-900);
  background: #fff;
}
.profile-wardrobe-page .primary-button {
  color: #fff;
  background: var(--forest-900);
  border-color: var(--forest-900);
}
.profile-wardrobe-page .primary-button:hover:not(:disabled) {
  color: #fff;
  background: var(--forest-950);
  border-color: var(--forest-950);
}
```

- [ ] **Step 5: 运行主题契约测试并确认通过**

Run: `npm test -- --run src/pages/ProfilePreferencesPage.test.tsx`

Expected: `1` 个测试文件通过，现有 `5` 项测试全部通过。

- [ ] **Step 6: 运行全量测试和生产构建**

Run: `npm test -- --run && npm run build`

Expected: 全部前端测试通过，TypeScript 与 Vite 构建成功。

- [ ] **Step 7: 浏览器视觉验证**

以 `VITE_DATA_MODE=mock` 启动本地页面，登录后检查 `/app/profile/wardrobe`：页面、卡片、标题、标签、输入值、空字段、选择框、日期框与文本域均为浅色米白主题；聚焦时显示绿色边框；桌面双栏和窄屏单栏保持可用。

- [ ] **Step 8: 提交实现**

```bash
git add frontend/src/pages/ProfilePreferencesPage.test.tsx frontend/src/pages/ProfilePreferencesPage.tsx frontend/src/styles/commerce.css docs/superpowers/plans/2026-07-19-profile-wardrobe-light-theme.md
git commit -m "fix: 统一衣橱画像浅色主题"
```
