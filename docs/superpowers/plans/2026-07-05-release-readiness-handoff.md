# 发布前收尾交接文档

> **给后续开发 Agent：** 六阶段路线完成后，从本交接文档继续。除非下面列出的阻塞项明确需要，否则不要在发布收尾阶段引入新的运行时基础设施。

**目标：** 把已经完成的六阶段开发成果整理成可审查、可测试、可合并的发布分支。

**当前分支：** `codex/user-feedback-loop`

**当前状态：** 六阶段路线均已实现。最新一次收尾修复了真实支付渠道的待支付幂等问题：同一个未支付订单重复发起 `ALIPAY` 或 `WECHAT` 支付时，现在会返回已有的 `PENDING` 支付流水，而不是再次创建服务商支付尝试。

## 已验证命令

按下面路径执行。

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -q -Dtest=PaymentServiceTests,PaymentControllerTests,PaymentMapperTests,AfterSaleServiceTests,AfterSaleControllerTests test
sh ./mvnw -q test
```

```bash
cd /Users/seekinward/Documents/推荐项目
sh scripts/check-local.sh
```

```bash
cd /Users/seekinward/Documents/推荐项目
git diff --check
```

## 阶段完成摘要

- 阶段 1：商品价格、库存等事实改为来自 Java 候选商品和 internal API，避免 Python 自己编造商品事实。
- 阶段 2：恢复共享 Java-Python 契约目录，统一放在 `outfit-project-contract`。
- 阶段 3：补齐可复现本地环境、根目录 `.env.example`、本地检查脚本和 CI 脚手架。
- 阶段 4：增加 Java assistant fallback，让 Python AI 异常时不影响电商主流程。
- 阶段 5：增加用户资料和偏好前端页面。
- 阶段 6：增加服务商形态的支付回调、验签成功路径，以及当前用户售后申请 API。

## 已知发布说明

- `MOCK` 支付仍保留为本地演示的确定性支付渠道。
- `ALIPAY` 和 `WECHAT` 目前是服务商形态骨架：创建 `PENDING` 支付并依赖签名回调确认成功，尚未接入真实支付 SDK。
- 回调密钥通过环境变量配置。不要提交真实生产密钥。
- `npm ci` 当前提示 6 个依赖漏洞。本地检查不会因此失败，但公开发布前需要单独评估。
- MQ 仍然暂缓引入。订单、库存、支付正确性继续保留在 Java 事务内。

## 推荐提交分组

如果要整理成易审查历史，建议按下面粒度提交：

1. 商品事实源对齐和 Python/Java 契约测试。
2. 共享契约目录和契约引用文档。
3. 本地环境、CI、Docker 和根目录验证脚本。
4. Assistant fallback 与 AI 韧性处理。
5. 用户偏好前端页面。
6. 真实支付回调、售后 API、待支付幂等修复。
7. 路线图和发布交接文档。

## 真实支付沙箱联调清单

接入生产服务商之前，先用非生产密钥做沙箱式联调：

1. 在运行环境中设置 `APP_PAYMENT_ALIPAY_CALLBACK_SECRET` 和 `APP_PAYMENT_WECHAT_CALLBACK_SECRET`。
2. 用 Docker Compose 启动 MySQL 和 Redis。
3. 启动 Java 后端和前端。
4. 创建用户、加入购物车、创建订单，然后调用 `POST /api/payments`，渠道传 `ALIPAY` 或 `WECHAT`。
5. 确认返回状态是 `PENDING`，并重复同一个支付请求，验证返回同一个 `paymentNo`。
6. 使用 Java 生成的 `paymentNo`、`orderNo` 和金额，向 `POST /api/payments/callback/{channel}` 发送签名回调。
7. 验证支付变为 `SUCCESS`，订单变为 `PAID`，库存只确认一次。
8. 重复发送同一回调，验证它被记录为重复回调，不会再次确认库存。
9. 分别发送无效签名、错误金额、错误渠道、错误订单号等负向回调，验证不会改变交易状态。

## MQ 决策

本次发布不建议新增 MQ，除非项目已经出现明确的异步工作负载。

后续适合 MQ 的候选：

- `payment.succeeded` 后的行为分析、推荐转化统计等副作用。
- RAG 重建、商品标签批处理、评测报告等 AI 批任务。
- 基于行为事件刷新用户画像的任务。

不适合作为第一批 MQ 的内容：

- 订单创建事务。
- 库存锁定、确认或释放事务。
- 支付成功幂等确认。

## 下一步可执行选择

1. 按推荐分组整理本地提交。
2. 推送当前分支并创建 Pull Request。
3. 执行真实支付沙箱回调联调清单。
4. 如果下一个产品目标是用户可见的退款/售后流程，则补前端售后入口。
