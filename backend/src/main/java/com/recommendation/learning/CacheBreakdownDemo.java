package com.recommendation.learning;

public class CacheBreakdownDemo {

    // 模拟 Redis 客户端
    static class Redis {
        public String get(String key) { return null; }
        public void set(String key, String value, int ttlSeconds) {}
        
        // 模拟 Redis 的 setnx 操作，获取分布式互斥锁
        public boolean tryLock(String lockKey) {
            return true; // 模拟获取成功
        }
        
        // 模拟释放分布式锁
        public void unlock(String lockKey) {}
    }

    // 模拟数据库
    static class Database {
        public String getProduct(String id) {
            System.out.println(">>> 极度危险：查询底层 MySQL <<<");
            return "RealProductData";
        }
    }

    private Redis redis = new Redis();
    private Database db = new Database();

    // 模拟让当前线程休眠一会儿
    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (Exception e) {}
    }

    /**
     * 核心任务：实现“缓存击穿”的互斥锁（Mutex Lock）防御方案。
     *
     * 场景推演：商品 id="999" 是大爆款，缓存刚好失效。此刻 10000 个并发请求同时调用这个方法。
     * 核心目标：只允许 1 个请求去查数据库并写回缓存，剩下的 9999 个请求原地等待，等第一个人写完后，直接从缓存读。
     *
     * 🧠 逻辑清单（Logic Checklist）：
     * 1. 查缓存，有数据直接返回。
     * 2. 没有缓存，尝试获取锁（调用 tryLock）。
     * 3. 如果获取锁【成功】：
     *    3.1 【极易错点 Double Check】拿到锁之后，必须再查一次缓存！为什么？（因为你可能是等了很久才拿到锁的，上一个人可能已经写好缓存了）。
     *    3.2 如果 Double Check 缓存依然没有，再去查数据库。
     *    3.3 写回缓存。
     *    3.4 释放锁（最好在 finally 块里，避免报错导致死锁）。
     * 4. 如果获取锁【失败】：
     *    4.1 说明别人正在查数据库，你只需 sleep(50) 毫秒。
     *    4.2 休眠结束后，重新调用本方法 getProductById(id)（递归重试）。
     */
    public String getProductById(String id) {
        String cacheKey = "product:" + id;
        String lockKey = "lock:product:" + id;

        // TODO: 请在这里手写你的互斥锁逻辑
        
        return null;
    }
}
