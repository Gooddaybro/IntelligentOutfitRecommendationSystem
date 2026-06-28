# Redis 缓存与 AI 限流手动验证说明

本文用于手动验证 Java 后端第一版 Redis 能力：

```text
1. 商品详情缓存：product:detail:{spuId}
2. 用户画像缓存：user:profile:{userId}
3. 推荐候选缓存：product:recommendation-candidates:{queryHash}
4. AI 聊天接口限流：assistant:rate-limit:user:{userId}:{minute}
```

验证目标不是看接口能不能返回，而是确认 Redis 里真的出现了对应 key，并且更新或限流行为符合预期。

---

## 0. 前置条件

先进入项目根目录：

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem
```

启动 Redis：

```bash
docker compose up -d redis
```

确认 Redis 正常：

```bash
docker exec -it intelligent_outfit_redis redis-cli ping
```

期望返回：

```text
PONG
```

启动 Java 后端。可以用 IDE 启动 `IntelligentOutfitRecommendationSystemApplication`，也可以在 `backend` 目录运行：

```bash
cd backend
sh mvnw spring-boot:run
```

如果本地 MySQL 使用 Docker Compose 的 `3307:3306` 映射，需要确保 Java 的 datasource 连接端口和你的本地环境一致。

---

## 1. Redis 常用查看命令

进入 Redis CLI：

```bash
docker exec -it intelligent_outfit_redis redis-cli
```

查看所有项目相关 key：

```redis
KEYS *
```

查看某一类 key：

```redis
KEYS product:detail:*
KEYS user:profile:*
KEYS product:recommendation-candidates:*
KEYS assistant:rate-limit:user:*
```

查看 key 内容：

```redis
GET product:detail:1001
```

查看 key 剩余过期时间，单位是秒：

```redis
TTL product:detail:1001
```

删除某个 key，方便重新测试未命中逻辑：

```redis
DEL product:detail:1001
```

退出 Redis CLI：

```redis
exit
```

注意：`KEYS *` 只适合本地开发验证。生产环境数据量大时不能随便用，会阻塞 Redis。

---

## 2. 验证商品详情缓存

### 2.1 清理旧缓存

```bash
docker exec -it intelligent_outfit_redis redis-cli DEL product:detail:1001
```

### 2.2 第一次请求商品详情

```bash
curl http://127.0.0.1:8080/api/products/1001
```

第一次请求时，Redis 没有缓存，Java 会查 MySQL，然后把结果写入 Redis。

### 2.3 查看 Redis key

```bash
docker exec -it intelligent_outfit_redis redis-cli KEYS 'product:detail:*'
```

期望能看到：

```text
product:detail:1001
```

查看内容：

```bash
docker exec -it intelligent_outfit_redis redis-cli GET product:detail:1001
```

期望看到一段 JSON，里面包含商品名、图片、价格、材质、季节、风格标签等字段。

查看 TTL：

```bash
docker exec -it intelligent_outfit_redis redis-cli TTL product:detail:1001
```

期望是一个大于 0 的秒数，通常接近 60 分钟，加上 0-5 分钟随机抖动。

---

## 3. 验证用户画像缓存

用户画像接口需要登录。先按 `docs/api-testing-with-reqable.md` 注册并登录，拿到 `accessToken`。

下面命令里把 `<accessToken>` 换成真实 token。

### 3.1 更新用户画像

```bash
curl -X PUT http://127.0.0.1:8080/api/me/profile \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "Redis Alex",
    "avatarUrl": "https://example.com/avatar.png",
    "gender": "male",
    "birthday": "1998-05-20"
  }'
```

更新接口会先写 MySQL，再删除旧的 `user:profile:{userId}` 缓存。

### 3.2 第一次读取画像

```bash
curl http://127.0.0.1:8080/api/me/profile \
  -H "Authorization: Bearer <accessToken>"
```

第一次读取时，如果缓存不存在，Java 会查 MySQL，然后写入 Redis。

### 3.3 查看用户画像 key

```bash
docker exec -it intelligent_outfit_redis redis-cli KEYS 'user:profile:*'
```

期望能看到类似：

```text
user:profile:1
```

查看内容：

```bash
docker exec -it intelligent_outfit_redis redis-cli GET user:profile:1
```

如果你的用户 ID 不是 `1`，需要把命令里的 `1` 换成实际 key 里的数字。

期望 JSON 里能看到：

```json
{
  "nickname": "Redis Alex"
}
```

---

## 4. 验证推荐候选缓存

推荐候选 key 使用查询条件 hash，因此 key 后半段不是人工可读文本。

### 4.1 清理旧推荐候选缓存

```bash
docker exec -it intelligent_outfit_redis redis-cli KEYS 'product:recommendation-candidates:*'
```

如果有旧 key，可以删除：

```bash
docker exec -it intelligent_outfit_redis redis-cli DEL <上一步看到的key>
```

### 4.2 请求推荐候选

```bash
curl "http://127.0.0.1:8080/internal/products/recommendation-candidates?category=外套&style=commute&season=autumn&budgetMax=400" \
  -H "X-Internal-Token: dev-internal-token"
```

第一次请求会查 MySQL，并写入 Redis。

### 4.3 查看推荐候选 key

```bash
docker exec -it intelligent_outfit_redis redis-cli KEYS 'product:recommendation-candidates:*'
```

期望看到类似：

```text
product:recommendation-candidates:4b6f...
```

查看内容：

```bash
docker exec -it intelligent_outfit_redis redis-cli GET <上一步看到的key>
```

期望看到一个 JSON 数组，里面是候选商品列表。

查看 TTL：

```bash
docker exec -it intelligent_outfit_redis redis-cli TTL <上一步看到的key>
```

期望 TTL 接近 10 分钟，加上 0-5 分钟随机抖动。

---

## 5. 验证 AI 聊天接口限流

AI 限流需要登录，并且 Java 会在调用 Python 前检查 Redis 计数。

默认限制：

```text
同一个用户每分钟最多 10 次
```

### 5.1 连续请求 AI 接口

把 `<accessToken>` 换成真实 token：

```bash
for i in {1..11}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://127.0.0.1:8080/api/assistant/chat \
    -H "Authorization: Bearer <accessToken>" \
    -H "Content-Type: application/json" \
    -d '{
      "message": "推荐一件通勤外套",
      "category": "外套",
      "style": "commute",
      "season": "autumn",
      "budgetMax": 400
    }'
done
```

期望前 10 次不是 `429`，第 11 次返回：

```text
429
```

如果 Python 服务没有启动，前 10 次可能返回 `502`，这是 Python 调用失败，不影响限流验证。重点看第 11 次应该变成 `429`。

### 5.2 查看限流 key

```bash
docker exec -it intelligent_outfit_redis redis-cli KEYS 'assistant:rate-limit:user:*'
```

期望看到类似：

```text
assistant:rate-limit:user:1:29745350
```

查看计数：

```bash
docker exec -it intelligent_outfit_redis redis-cli GET <上一步看到的key>
```

期望值大于等于：

```text
11
```

查看 TTL：

```bash
docker exec -it intelligent_outfit_redis redis-cli TTL <上一步看到的key>
```

期望小于等于 60 秒。

---

## 6. Redis 验证完成标准

本地手动验证通过的标准：

```text
商品详情请求后能看到 product:detail:{spuId}
用户画像读取后能看到 user:profile:{userId}
推荐候选请求后能看到 product:recommendation-candidates:{queryHash}
AI 连续请求超过阈值后返回 429
AI 限流 key 的 TTL 大约为 60 秒
```

如果这些都满足，说明 Redis 第一版四个核心点已经实际接入，而不是只写了配置或依赖。
