# Task 3：Java Pro 上下文与独立入口

范围：仅完成 Task 3。基于 Task 2 提交 `93434b3`，在隔离分支 `codex/pro-task-03` 实施；未合并至原项目工作目录。

## 已实现

- 新增 `POST /api/assistant/v2/chat`，使用认证主体中的用户 ID，独立 `ProChatRequest`，不修改 Lite 的 DTO 或调用流程。
- 前端只提供 message、threadId、agentMode=pro 和明确筛选条件。伪造的 user_context、candidates、运行凭证等额外字段被忽略。
- Java 先校验会话归属，再保存用户消息，创建绑定用户/会话/请求的 run，装配可信资料、历史和明确筛选条件。
- 显式筛选与画像软偏好分开；不调用旧需求解析器、旧澄清分支或预先召回商品。当前消息存储没有结构化商品引用，因此 current_product_refs 暂为 []。
- 独立客户端向 Python `/v2/chat` 发送 snake_case v2 请求。服务凭据来自现有 app.ai.python-internal-token 配置，运行凭证来自 Java registry；禁用重定向，不自动重试或回退 Lite。
- 校验响应关联的 contract_version、agent_mode、request_id、run_id、thread_id 和基本结构。无论上下文构建或调用成功/失败，均尝试撤销 run 凭证。

## 阶段边界与配置

`APP_AI_PRO_ENABLED` 默认 false，返回 HTTP 503 / pro_disabled。

`APP_AI_PRO_READ_TIMEOUT_MS` 默认 110000，允许正值且不得超过 120000 毫秒。

当前开启开关只允许验证内部调用链：调用成功仍返回 HTTP 503 / pro_result_not_ready，未经最终业务校验的 Python 回答不传给前端、不保存成助手消息。开启后的请求可能创建会话及保存用户消息，因此此阶段保持默认关闭。

本次不代表完整 Pro 已可使用：真实 Python 调度、最终商品校验、SSE 和商品卡片尚待后续任务。Task 7 将在 run 撤销前接入最终验证，Task 9 接入 SSE。本次 Python 传输测试使用本地 HTTP 测试服务，不代表真实模型联调通过。

## 验证

- 新增测试共 18 项：独立入口 3、服务 5、可信上下文 5、HTTP 客户端 4，以及原 Controller 测试类中的 Pro 未认证请求 1；最终回归均通过。
- 历史乱序回归先确认失败，再修复：仅接纳请求 ID 一致的相邻成功问答，排除本轮和缺少关联 ID 的历史。宁可不传无法可靠关联的历史，不拼错问答。
- `mvnw.cmd verify` 实际执行，结果 566 项、0 失败、2 错误、11 跳过。两项错误为现有 AiTaskRetryDlqIntegrationTests / RabbitAiTaskTopologyTests 找不到 Docker 环境；该次执行后又补了历史乱序与未认证请求测试。
- 最终执行 `mvnw.cmd "-Dtest=*,!AiTaskRetryDlqIntegrationTests,!RabbitAiTaskTopologyTests" verify`：566 项，555 通过、11 跳过、0 失败、0 错误，BUILD SUCCESS，Checkstyle 0 违规。该命令明确排除了上述两类 Docker 测试；本轮未开启可选真实 Redis 专项测试，不能据此声称外部依赖联调通过。
- 日志在隔离工作树的 backend/target/task03-*.log；`git diff --cached --check` 通过。

## Task 4 下一步

仅在 Python 中建立 `clothing_assistant/agent/pro/__init__.py`、`schemas.py`、`state.py` 和 `tests/test_pro_state.py`：

1. 定义严格的 tool / clarify / finish 三种行动；参数互斥、未知字段拒绝、工具名称限制在白名单。
2. 实现 `parse_action(payload)`，使非法行动在执行前失败。
3. 实现 `new_state(run_id, query, hard_constraints)`，每次运行单独分配需求、证据、工具结果等集合，杜绝跨轮共享。
4. 保存用户硬约束及来源，工具反馈不能覆盖原始约束；需求状态升级必须检查对应 evidence_id。
5. 先写失败测试，再实现并通过 `python -m pytest tests/test_pro_state.py -q`。

Task 4 完成后仍不会执行真实工具循环：工具适配属于 Task 5，动态执行器属于 Task 6。
