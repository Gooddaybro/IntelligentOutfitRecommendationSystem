# Pro Task 2：受控查询与本轮候选登记

## 本次范围

只实现 Pro 的 Java 内部查询基础，不接入 Pro 聊天入口、Python 决策循环或前端按钮。Lite 原有流程保持不变。

Task 1 的版本化协议副本位于 [v2.md](../../../contracts/assistant-streaming-chat/v2.md)。本次新增服务读取真实 Java 商品数据，不能接受模型自己提供的价格、库存或商品候选。

## 调用过程

1. Java 服务端创建一个运行凭据，绑定用户、会话与请求标识；后续 Task 3 才会把创建过程接入聊天生命周期。
2. Python 使用内部服务凭据和本轮凭据调用查询网关。
3. 网关只允许商品搜索、商品详情和指定颜色尺码的库存查询。
4. 实际查询到的商品写入本轮登记表，供后续最终回答校验使用。
5. 请求关闭或过期后，旧凭据不再允许查询或登记。

端点形式：

```text
POST /internal/assistant/runs/{runId}/tools/{toolName}
X-Internal-Token: 服务端配置的内部凭据
X-Pro-Run-Token: Java 创建的本轮凭据
```

预算搜索参数示例：

```json
{"category":"外套","budget_max":"299.90"}
```

库存参数示例：

```json
{"spu_id":1001,"color":"黑色","size":"L"}
```

这些示例需要真实运行凭据才可执行。没有新增供前端绕过聊天授权创建凭据的接口。

## 如何查看本次行为

主要通过单元测试、HTTP 边界测试和真实 Redis 脚本测试审阅。重点观察：错误凭据不能读取商品；299.90 的预算不会被截断为 299；零库存与依赖失败不同；并发登记不突破容量，也不会复活已关闭请求。

完整 Pro 界面、LLM 自动换商品和最终商品卡片都不属于本次验收。下一项仍为 Task 3，不自动开始。

## 验证方式

在 `backend` 目录执行：

```powershell
$env:MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
./mvnw.cmd '-Dtest=ProToolQueryServiceTests,ProRunRegistryTests,ProRunRegistryFailureTests,InternalAssistantToolControllerTests' '-Dpro.test.redis.port=16382' test
./mvnw.cmd '-Dpro.test.redis.port=16382' verify
./mvnw.cmd '-Dpro.test.redis.port=16382' '-Dtest=*,!AiTaskRetryDlqIntegrationTests,!RabbitAiTaskTopologyTests' verify
```

`pro.test.redis.port` 指向测试用 Redis。实际 Lua 测试只写随机运行标识对应的键并自行清理，不执行 FLUSHDB。没有指定该属性时，真实 Redis 测试会明确跳过；跳过不代表通过。

本次使用 WSL 内临时 Redis 7.0.15，监听本机 16382，关闭持久化，没有修改项目默认 Redis 配置。新增专项测试 21 项通过、0 失败、0 跳过，其中 3 项执行了真实 Redis 脚本。

完整 `verify` 首轮发现助手不能依赖 `ProductCatalogService` 的架构规则，现已通过 `ProductFactsQuery` 只读接口修正，未放宽架构测试。同时，两项既有 RabbitMQ 测试因 Docker 未启动报错。

明确排除 `AiTaskRetryDlqIntegrationTests` 和 `RabbitAiTaskTopologyTests` 后，`verify` 成功：报告 551 项，540 项通过、11 项跳过、0 失败、0 错误；打包成功，Checkstyle 0 违规。6 项模块架构测试全部通过。不能把本次结果视作所有容器集成测试均已验证；完整日志保存在本工作区 `backend/target/task02-verify.log` 和 `backend/target/task02-verify-available.log`。

## 实现文件

- `ProRunRegistry`：服务端运行凭据、不可续期的 10 分钟 TTL、原子候选登记和关闭。
- `ProToolQueryService`：三个只读工具、严格参数、真实商品字段、精确预算过滤。
- `InternalAssistantToolController`：HTTP 路由和安全的错误分类，沿用内部服务鉴权。
- `ProToolResult`：v2 snake_case 返回格式。
- `ProductFactsQuery`：商品模块对外只读接口，现有商品实现行为保持不变。

运行凭据不能通过本次新增 HTTP 接口创建；其接入由 Task 3 完成。本次仅在隔离分支 `codex/pro-task-02` 交付，未合并至原始工作区。
