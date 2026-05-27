# Reqable 接口测试说明

本文用于手动验证当前 Java 后端接口。先启动 Spring Boot 应用，再按下面顺序测试。

## 1. 环境变量

```text
base_url = http://127.0.0.1:8080
internal_token = dev-internal-token
access_token = 登录后复制 data.accessToken
refresh_token = 登录后复制 data.refreshToken
```

## 2. 注册用户

```http
POST {{base_url}}/api/auth/register
Content-Type: application/json
```

```json
{
  "username": "tester001",
  "password": "StrongPassword123!",
  "email": "tester001@example.com"
}
```

期望：`200 OK`，返回 `data.userId`、`data.username`、`data.status=active`。

## 3. 登录并获取 Token

```http
POST {{base_url}}/api/auth/login
Content-Type: application/json
```

```json
{
  "username": "tester001",
  "password": "StrongPassword123!"
}
```

期望：`200 OK`，复制：

```text
data.accessToken  -> access_token
data.refreshToken -> refresh_token
```

## 4. 测试当前用户

不带 token：

```http
GET {{base_url}}/api/users/me
```

期望：`401 Unauthorized`。

带 token：

```http
GET {{base_url}}/api/users/me
Authorization: Bearer {{access_token}}
```

期望：`200 OK`，返回 `ROLE_USER`。

## 5. 测试用户画像

```http
PUT {{base_url}}/api/me/profile
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "nickname": "Alex",
  "avatarUrl": "https://example.com/avatar.png",
  "gender": "male",
  "birthday": "1998-05-20"
}
```

读取：

```http
GET {{base_url}}/api/me/profile
Authorization: Bearer {{access_token}}
```

## 6. 测试身体数据

```http
PUT {{base_url}}/api/me/body-data
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "heightCm": 178.5,
  "weightKg": 70.2,
  "shoulderWidthCm": 45.0,
  "bustCm": 96.0,
  "waistCm": 80.0,
  "hipCm": 95.0,
  "preferredFit": "regular"
}
```

读取：

```http
GET {{base_url}}/api/me/body-data
Authorization: Bearer {{access_token}}
```

## 7. 测试穿衣偏好

```http
PUT {{base_url}}/api/me/preferences
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "preferredStyles": ["commute", "minimal"],
  "preferredColors": ["black", "navy"],
  "dislikedColors": ["orange"],
  "preferredCategories": ["jacket", "pants"],
  "budgetMin": 100,
  "budgetMax": 500
}
```

读取：

```http
GET {{base_url}}/api/me/preferences
Authorization: Bearer {{access_token}}
```

## 8. 刷新与登出

刷新：

```http
POST {{base_url}}/api/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "{{refresh_token}}"
}
```

期望：返回新的 `accessToken` 和 `refreshToken`。旧的 refresh token 会被撤销。

登出：

```http
POST {{base_url}}/api/auth/logout
Content-Type: application/json
```

```json
{
  "refreshToken": "{{refresh_token}}"
}
```

期望：`200 OK`。再次用同一个 refresh token 调 `/api/auth/refresh` 应返回 `400 Bad Request`。

## 9. Internal API

普通 Bearer Token 不能替代内部 token。Python AI 服务调用 internal API 时仍然使用：

```http
GET {{base_url}}/internal/inventory?skuId=2001
X-Internal-Token: {{internal_token}}
```
