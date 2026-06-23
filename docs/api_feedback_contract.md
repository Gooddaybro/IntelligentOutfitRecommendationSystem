# AI 推荐反馈功能 API 契约文档 (API Contract)

本文档定义了“AI推荐结果点赞/踩”功能在前端、Java核心系统与Python AI服务之间的数据交互格式。

## 1. 前端 -> Java 后端接口

该接口由前端发起，Java 侧的 Spring Boot 负责接收。Java 会在拦截器中验证用户身份，然后将数据处理后转发给 Python AI 服务。

- **请求路径**: `POST /api/chat/feedback`
- **功能描述**: 记录用户对某条 AI 推荐消息的点赞或踩。

### 1.1 请求头 (Headers)
| 参数名称 | 必填 | 描述 |
| :--- | :--- | :--- |
| `Authorization` | 是 | 用户的 Bearer Token（用于验证用户身份并提取 User ID） |
| `Content-Type` | 是 | `application/json` |

### 1.2 请求体 (Body)
```json
{
  "sessionId": "chat_session_8a92b1",  // 当前聊天会话的唯一标识
  "messageId": "msg_992103_xyz",        // 被点赞/踩的那条具体 AI 消息的唯一标识
  "feedbackType": "LIKE"                // 态度反馈。枚举值："LIKE" (点赞) 或 "DISLIKE" (踩)
}
```

### 1.3 响应体 (Response)
Java 端返回统一格式的标准响应体：
```json
{
  "code": 200,                          // 200 表示成功
  "message": "Feedback recorded successfully", 
  "data": null                          // 该接口通常不需要返回具体数据
}
```

---

## 2. Java 后端 -> Python AI 后端接口

该接口由 Java 后端在验证身份后，使用 HTTP Client 向 Python FastAPI 发起内部调用。

- **请求路径**: `POST /chat/feedback`
- **功能描述**: 将带着用户 ID 的完整反馈数据，投递给 Python AI 服务进行日志落盘和后续模型优化。

### 2.1 请求头 (Headers)
| 参数名称 | 必填 | 描述 |
| :--- | :--- | :--- |
| `Content-Type` | 是 | `application/json` |

### 2.2 请求体 (Body)
*注意：Java 在转发时，将 Token 解析出的 `userId` 以及系统当前时间 `timestamp` 添加进了请求中，确保 Python 侧拥有完整的上下文。*
```json
{
  "userId": "user_10086",               // Java 层解析出并注入的用户唯一 ID
  "sessionId": "chat_session_8a92b1",   // 会话 ID
  "messageId": "msg_992103_xyz",        // 消息 ID
  "feedbackType": "LIKE",               // 反馈类型： "LIKE" | "DISLIKE"
  "timestamp": "2026-06-23T15:10:00Z"   // Java 端打上的标准时间戳
}
```

### 2.3 响应体 (Response)
Python 端返回给 Java 端的响应格式：
```json
{
  "status": "success",
  "message": "Feedback recorded"
}
```
