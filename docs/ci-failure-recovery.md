# CI 文档提交失败修复记录（2026-09-09）

## 已确认的根因

- [纯文档提交失败记录](https://github.com/Gooddaybro/IntelligentOutfitRecommendationSystem/actions/runs/29195130943)：提交 a43a1a4 仅修改四份 Markdown；仍运行 Java 测试，192 项中 2 项失败。
- [后续失败记录](https://github.com/Gooddaybro/IntelligentOutfitRecommendationSystem/actions/runs/33287995781)：538 项中 2 项失败。
- 两次失败均为 SharedJavaPythonContractTests 无法找到相邻目录的 `outfit-project-contract/contracts/java-python-chat/v1.fields.json`。GitHub checkout 只有当前仓库，不包含开发机的相邻共享目录。没有证据表明 Word 文件内容导致失败。

## 本次修复

- 保留 `Java CI / test` 检查，不用工作流级 paths-ignore，避免文档 PR 的必需检查一直等待。
- 根目录或 docs/ 下的已知文档扩展名变更跳过 Java 重测试；混合代码、契约、工作流、脚本、未知路径继续验证。手动运行、新分支、无法获取差异也默认验证。
- 文档提交仍运行轻量分类器测试。代码推送到 water、master、main，以及目标为 master/main 的 PR，运行 Java verify；保留真实 MySQL 测试开关。
- 测试资源内提交字段契约快照，独立 checkout 使用快照；存在相邻共享源时优先读取共享源，并断言快照一致，防止静默漂移。
- 修改共享字段时，必须同步共享源、仓库内快照和两端模型，运行共享契约测试；不能只为让测试通过而修改快照。

## 验证与边界

- 本轮只实现两层：文档轻量检查、非文档 Java 验证；不新增 Docker、镜像发布或跨服务 E2E。
- 本地 `mvnw verify` 成功：546 项测试，0 失败、0 错误、12 跳过，Checkstyle 0 违规。此结果不等同于开启全部基础设施测试后的远端结果。
- 分类器单测：`python3 -m unittest discover -s scripts -p test_ci_changes.py`。
- 独立临时 checkout（不带相邻共享目录）的共享契约测试：3 项通过，覆盖远端缺文件场景。
- 下单小项：client/CheckoutPage 15 项、OrderControllerTests 14 项通过；没有重新实现已有下单逻辑。
- 远端 Actions 是否恢复，必须以推送修复后的新运行结果为准；本地结果不能替代远端验收。
- 快照只是当前 CI 的过渡方案。阶段 7 的独立可版本化共享源、Java/Python revision 配对、跨服务黄金链路和分层 CI 仍待完成，未在本次扩大实施范围。
