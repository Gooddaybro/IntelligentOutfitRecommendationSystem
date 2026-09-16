# Task 10：前端模式、进度和共用商品卡片

本任务在 Java/前端工作区 `codex/pro-task-07` 完成，代码提交为 `feat: 增加 Pro 模式和核验商品展示`。

## 本次实现

- `frontend/src/shared/api/types.ts` 增加 `AgentMode` 和 Pro done 商品事实字段，Lite 请求类型保持兼容。
- `frontend/src/shared/api/assistantStream.ts` 增加 v2 SSE 路径选择、meta/runId、progress、Pro done 事实解析和结构化错误消息解析；Lite 仍使用旧 SSE 地址。
- `frontend/src/features/assistant/ChatPanel.tsx` 增加可访问的 Lite/Pro 选择器。发送后锁定本轮模式，Pro 运行时按 `run_id` 和递增序号展示进度，切换模式不会清空历史。
- Pro 的商品卡片只在 Java `done` 到达后创建，直接使用 `name`、`sale_price`、`main_image_url`、颜色、尺码和库存等 Java 已核验字段，不再通过旧候选快照二次筛选；无效或缺少关键事实的商品引用会被丢弃。
- `frontend/src/features/assistant/assistantState.ts` 持久化当前模式、活动 run 和进度；新一轮请求清理旧商品和旧进度。Pro 请求失败只展示错误，不静默回退到 Lite。
- `frontend/src/pages/AiShoppingPage.tsx` 在新一轮开始时清理旧卡片，并用加载代次保护初始候选请求，继续复用现有 `ProductCard` 和商品详情路由。

## 验证记录

- 前端全量单测：`30 test files, 87 tests passed`。
- `npm run build`：通过，TypeScript 检查和 Vite 生产构建成功。
- `git diff --check`：通过。

## 完成 Task 10 后还剩什么

| 阶段 | 必须完成的工作 | 用户能看到的变化 |
| --- | --- | --- |
| Task 11 | 固定场景评测、Lite/Pro 对比、Java→Python→Java 联调、E2E 和测试环境启用 | 有证据判断动态 Agent 的正确性和实际模型效果 |

Pro Max 多 Agent 仍不在本轮实现范围。当前页面已经能选择 Pro、看到过程状态，并展示 Java 最终校验后的商品卡片。
