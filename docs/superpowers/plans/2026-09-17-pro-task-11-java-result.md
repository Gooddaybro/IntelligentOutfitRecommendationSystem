# Task 11：Java / 前端纵向验收记录

Java 主仓库接入 Python Pro v2 的已推送 revision，并增加本地/测试环境的 Pro 运行配置和前端验收。

## 已交付

- `.services/python-revision` 更新为 Python 仓库已推送的 `1eef95c1a29479156ef8c47a6afb0914dc25568d`，主仓库 CI 可以按固定 SHA 取到 Task11 评测代码。
- `docker-compose.demo.yml` 为 Python 配置内部 Java 回调地址和同一内部 token，为 Java 配置 `APP_AI_PRO_ENABLED` 与读取超时；默认仍为关闭。
- `.env.demo.example` 增加 Pro 开关、调用上限和超时示例，只有本地/测试 `.env` 明确设置 `APP_AI_PRO_ENABLED=true` 才启用。
- `frontend/e2e/pro-assistant.spec.ts` 用假 v2 SSE 覆盖 Lite → Pro 切换、进度事件、A 无货后 B 商品卡片、推荐理由和详情跳转。
- `frontend/e2e/integration/pro-assistant.spec.ts` 提供显式 `RUN_PRO_INTEGRATION=true` 的真实 Java → Python → Java 取消测试；默认跳过，避免普通假服务 E2E 误访问外部依赖。

## 验证命令与结果

| 命令 | 结果 |
| --- | --- |
| `npm run test -- --run` | 31 files / 93 tests passed |
| `npm run build` | passed |
| `npm run test:e2e -- e2e/pro-assistant.spec.ts` | 1 passed |
| `npm run test:e2e:integration -- e2e/integration/pro-assistant.spec.ts` | 1 skipped（需要 `RUN_PRO_INTEGRATION=true`） |
| `docker compose -f docker-compose.yml -f docker-compose.demo.yml config --quiet` | passed |
| `./mvnw.cmd "-Dtest=ProAssistantStreamTests,ProAssistantServiceTests,ProAssistantControllerTests,ProPythonAssistantClientTests,ProRecommendationValidatorTests" test` | 22 tests，0 failures，0 errors，0 skipped |
| `./mvnw.cmd verify` | 585 tests，583 passed，14 skipped，2 errors |

Maven 全量的两个错误是 `AiTaskRetryDlqIntegrationTests` 和 `RabbitAiTaskTopologyTests` 启动 Testcontainers 时找不到 Docker 环境；本机 Docker Desktop Linux engine named pipe 不存在。其余测试没有失败。真实 Docker demo 及正向/取消联调需在 Docker daemon、MySQL、Redis、RabbitMQ、Elasticsearch、Postgres、Java、Python 和模型服务齐备的测试机执行。
