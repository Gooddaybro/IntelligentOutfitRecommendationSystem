# Python AI API Contract Reference

CI 使用的版本化契约见：

```text
contracts/java-python-chat/v1.fields.json
contracts/rag-rebuild/schemas/
```

当前 Java 侧确认契约版本：v1（DemandIntent 使用 demand-intent-v3）
确认日期：2026-09-04
适配状态：Java DTO、Python Pydantic Model、同步响应和 SSE 最终事件由同一份字段契约约束

Java 侧适配设计文档见：

```text
docs/contracts/java-python-chat-contract-adaptation.md
```

兼容的 Python 提交固定在 `.services/python-revision`。修改字段契约或升级该 revision 时，必须同时通过 `shared-contract` 和三次真实跨服务黄金链路。测试可通过 `OUTFIT_CONTRACT_ROOT` 指向本目录，禁止依赖开发者机器上的未提交绝对路径。
