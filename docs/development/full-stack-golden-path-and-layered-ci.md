# 全栈黄金链路与分层 CI 开发说明

## 1. 当前实现状态

截至 2026-09-04：

- Docker Compose 可以从空数据卷启动 MySQL、Redis、RabbitMQ、PostgreSQL、Elasticsearch、Java Web、Java Worker、Python AI 和 React/Nginx。
- 黄金链路不拦截浏览器 HTTP，不使用 HAR，不伪造 Java、Python 或数据库边界。
- 外部 Kimi/Jina 只在 Provider Adapter 层替换为确定性实现，并且只能在 `test` 或 `integration` 环境启用。
- 本地从空卷连续执行三次黄金链路均通过。
- PR CI 已拆成独立质量门禁，跨服务工作流固定 Python commit SHA。
- GitHub Actions 远端执行仍需要先推送两个开发分支；本地完成不等于远端工作流已经运行。

## 2. 容器启动方式

项目默认需要两个相邻仓库：

```text
推荐系统/
├── Intelligent Outfit Recommendation System/
└── AI Clothing Shopping Assistant System/
```

国内网络环境：

```powershell
cd "D:\git\推荐系统\Intelligent Outfit Recommendation System"
Copy-Item .env.daocloud.example .env
sh scripts/start-demo.sh
```

完整集成验证不要求手工启动服务，Windows 使用：

```powershell
.\scripts\run-golden-path.ps1 -EnvFile .\.env.daocloud.example
```

Linux/GitHub Actions 使用：

```bash
ENV_FILE=.env.demo.example sh scripts/run-golden-path.sh
```

运行器会执行：

```text
删除旧容器和数据卷
→ 校验 Compose
→ 构建并启动真实应用镜像
→ 等待全部健康检查
→ 重建 RAG 索引并校验 `chunkCount > 0`
→ 执行真实 RAG 检索并校验 `source_count > 0`
→ 执行 Playwright
→ 失败时收集并脱敏证据
→ 删除容器和数据卷
```

除前端固定使用 3000 端口外，集成运行器为基础设施和内部应用申请随机宿主机端口，避免与本机 MySQL、Redis 等服务冲突。

## 3. 黄金链路覆盖范围

文件：

- `frontend/e2e/integration/golden-path.spec.ts`
- `frontend/playwright.integration.config.ts`
- `scripts/run-golden-path.ps1`
- `scripts/run-golden-path.sh`

链路：

```text
注册并登录
→ Java 获取候选
→ Java 调用真实 Python SSE
→ 验证 Python 商品引用属于 Java 候选集
→ 加入购物车并明确勾选
→ 创建默认地址
→ 服务端结算预览
→ 创建订单
→ Mock 支付
→ 查询订单与支付状态
→ 验证金额和地址快照
→ 重复支付幂等
→ 重复下单幂等
→ 改变地址复用幂等键返回冲突
→ 跨用户地址访问返回 404
```

测试只替换外部模型和 Embedding Provider。React、Nginx、Java、Python、MySQL、Redis、RabbitMQ、Elasticsearch 和 PostgreSQL 都是真实进程。

## 4. 确定性 AI Provider

Python 提交中的 `deterministic_provider.py` 提供：

- 固定且可追踪的聊天结果。
- 固定需求解析结果。
- 相同输入始终相同的 16 维 Embedding。
- 不访问 Kimi/Jina 网络。

启用条件：

```text
AI_RUNTIME_ENV=test|integration
AI_DETERMINISTIC_PROVIDER=true
```

在 `development` 或 `production` 环境启用会直接失败，防止测试替身进入正常运行环境。

## 5. 分层 CI

### 5.1 PR 快速门禁

`.github/workflows/ci.yml` 包含：

| Job | 内容 |
| --- | --- |
| `shared-contract` | Java 与 Python 同时验证版本化契约 |
| `backend-verify` | Maven `clean verify`、MySQL Testcontainers、Checkstyle |
| `python-quality-and-tests` | pytest、Ruff、compileall、interrogate |
| `frontend-test-and-build` | Vitest 与 production build |
| `container-config-and-build` | Compose config 和三个应用镜像构建 |

所有 Job 都设置超时；Maven、npm、pip 使用官方缓存；同一分支的新执行会取消旧执行。纯文档改动可以跳过容器构建，但 `contracts/**` 变更仍进入契约和容器门禁。

### 5.2 跨服务门禁

`.github/workflows/cross-service.yml` 在以下情况运行：

- `master` 或 `main` push。
- 修改 Python revision、契约、应用、Compose 或黄金链路的 PR。
- 每日定时任务。
- 手动 `workflow_dispatch`。

工作流从 `.services/python-revision` 读取 40 位 commit SHA，检出明确的 Python 版本，然后从空卷连续执行三次黄金链路。失败时上传：

- `docker compose ps`
- 脱敏 Compose 日志
- 脱敏 Playwright 控制台日志

不会上传 `.env`、Playwright screenshot、video 或 trace。浏览器原始证据可能含 JWT、Authorization Header 和用户输入，因此只保留经过同一脱敏器处理的文本日志；脱敏器会处理 Authorization、JWT、密码、URL 凭据、邮箱和手机号。

## 6. Python revision 升级顺序

由于主仓库按 SHA 检出 Python，发布顺序必须是：

1. Python 分支完成测试并提交。
2. 先把 Python commit 推送到远端，使 SHA 可被 GitHub 检出。
3. 更新主仓库 `.services/python-revision`。
4. 主仓库 PR 同时运行契约和三次黄金链路。
5. 全部门禁通过后再合并。

禁止把 revision 指向尚未推送或可能被 rebase 删除的 commit。

## 7. 版本化共享契约

主仓库 `contracts/` 是 CI 使用的版本化契约快照，包括：

- `java-python-chat/v1.fields.json`
- 三份 RAG rebuild JSON Schema
- RAG rebuild v1 说明

Java 测试优先读取仓库内 `contracts/`，也支持 `OUTFIT_CONTRACT_ROOT`。Python 仓库保留同版本快照，使固定 Python SHA 的独立 CI 不依赖主仓库可变分支；跨服务 CI 则通过 `OUTFIT_CONTRACT_ROOT` 强制 Python 验证主仓库当前契约，因此组合兼容性仍由主仓库快照裁决。

## 8. 本次发现并修复的问题

| 问题 | 原因 | 修复 |
| --- | --- | --- |
| Linux 镜像无法执行 `mvnw` | Windows checkout 将脚本写成 CRLF | `.gitattributes` 固定 `backend/mvnw` 和 Shell 脚本为 LF |
| Java Web/Worker 镜像并行构建冲突 | 两个 Compose 服务同时构建同一 image tag | 只由 `backend-web` 构建，Worker 复用镜像 |
| 本机 3307 端口冲突 | 本机已有 MySQL 监听 | 集成运行器给内部服务使用随机宿主机端口 |
| 结算按钮一直禁用 | 测试加入购物车后没有勾选商品 | Playwright 明确勾选并断言按钮可用 |
| 契约测试依赖开发者本地目录 | 契约目录没有进入应用仓库版本关系 | 将快照纳入主仓库并支持 `OUTFIT_CONTRACT_ROOT` |
| 空镜像内 RAG 静默降级为空结果 | `.dockerignore` 排除了本地索引，启动时也未重建 | 增加 `rag-index-init` 和 `rag-retrieval-check`，分别断言 `chunkCount > 0` 与 `source_count > 0` |
| CI 原始浏览器证据可能泄露认证和用户数据 | trace、video、screenshot 未经过文本脱敏器 | 远端只上传脱敏 Compose/Playwright 文本日志和容器状态 |
| Python 独立 CI 读取主仓库可变 `master` | 同一 Python SHA 的结果可能随主分支变化 | Python 仓库纳入契约快照，独立 CI 只读取当前提交 |

## 9. 本地验证记录

2026-09-04 的验证结果：

```text
黄金链路空卷连续运行：3/3 通过
Java Maven verify：547 tests，0 failures，0 errors，2 skipped
Python pytest：293 passed，104 subtests passed
Python Ruff：通过
Python compileall：通过
前端 Vitest：30 files / 78 tests 通过
前端 production build：通过
脚本与 CI 配置测试：12 tests 通过
Compose config：通过
Workflow YAML 语法解析：通过
```

Java 测试退出阶段存在 Lettuce event-loop shutdown 日志，但 Maven 最终结果为成功且没有测试失败。该日志属于后续测试日志洁净度优化，不影响本阶段门禁结论。

## 10. 远端激活前检查

当前提交仍在本地开发分支。启用 GitHub Actions 前必须确认：

- Python commit 已推送。
- 主仓库分支已推送。
- GitHub Actions 对两个仓库具有读取权限。
- 默认分支中的主仓库已经包含 `contracts/`。
- 首次远端运行的镜像和依赖下载时间未超过 Job timeout。
