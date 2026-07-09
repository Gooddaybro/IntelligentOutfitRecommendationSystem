package com.recommendation.learning;

public class CachePenetrationDemo {

    // 模拟 Redis 客户端
    static class Redis {
        public String get(String key) {
            return null; // 模拟未命中
        }
        public void set(String key, String value, int ttlSeconds) {
            System.out.println("写入 Redis -> key: " + key + ", value: " + value + ", ttl: " + ttlSeconds + "s");
        }
    }

    // 模拟 MySQL 数据库
    static class Database {
        public String getProduct(String id) {
            return null; // 模拟数据库也没有这个商品
        }
    }

    private Redis redis = new Redis();
    private Database db = new Database();

    /**
     * 核心任务：请在这里实现“防缓存穿透（缓存空值）”的代码逻辑！
     *
     * 提示：
     * 1. 空值的特殊标识：你可以用一个特定的字符串（例如 "<EMPTY>" 或 "{}"）来代表数据库里也没有的数据。
     * 2. 注意真正的数据 TTL 与空值的 TTL 应该有什么区别？
     */
    public String getProductById(String id) {
        String cacheKey = "product:" + id;

        // 1. 先查 Redis
        String cacheValue = redis.get(cacheKey);

        // 2. Redis 命中的两种情况
        if (cacheValue != null) {
            if ("<EMPTY>".equals(cacheValue)) {
                // 情况 A：命中了我们以前故意放进来的“空值标记”，说明数据库一定没有，直接返回 null
                return null;
            }
            // 情况 B：命中了真实的业务数据，直接返回
            return cacheValue;
        }

        // 3. Redis 里啥也没有（不管是真数据还是空标记都没命中），只能去查 MySQL
        String dbValue = db.getProduct(id);

        // 4. 根据 MySQL 的查询结果写回 Redis
        if (dbValue == null) {
            // 核心防御点：如果 MySQL 也查不到，一定要写一个特殊的“空值标记”到 Redis。
            // 注意：空值的过期时间一定要设置得【短一点】（例如 5 分钟）。
            // 因为如果有人马上把这个商品录入到了数据库，如果缓存了 1 天的空值，用户 1 天内都看不到新商品。
            redis.set(cacheKey, "<EMPTY>", 5 * 60);
            return null;
        } else {
            // 正常流程：从 MySQL 查到了，写回 Redis，过期时间可以【长一点】（例如 1 小时）
            redis.set(cacheKey, dbValue, 60 * 60);
            return dbValue;
        }
    }
}
