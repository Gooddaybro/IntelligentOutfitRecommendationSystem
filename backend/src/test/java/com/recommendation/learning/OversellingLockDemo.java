package com.recommendation.learning;

import org.springframework.transaction.annotation.Transactional;

public class OversellingLockDemo {

    // 模拟数据库表里的一条数据
    static class ProductEntity {
        int stock;
        int version;
        public ProductEntity(int stock, int version) { this.stock = stock; this.version = version; }
    }

    // 模拟数据库 Mapper 操作
    static class DatabaseMapper {
        // 普通查询（无锁） 对应 SQL: SELECT stock, version FROM product WHERE id = ?
        public ProductEntity getProduct(String productId) {
            return new ProductEntity(1, 100); // 假设只剩 1 件，当前版本号 100
        }

        // 悲观锁查询 对应 SQL: SELECT stock FROM product WHERE id = ? FOR UPDATE
        public ProductEntity getProductForUpdate(String productId) {
            return new ProductEntity(1, 100); 
        }

        // 普通更新 对应 SQL: UPDATE product SET stock = ? WHERE id = ?
        public void updateStock(String productId, int newStock) {
            System.out.println("【悲观锁路线】因为已经锁门了，直接覆盖库存为: " + newStock);
        }

        // 乐观锁更新 对应 SQL: UPDATE product SET stock = stock - 1, version = version + 1 WHERE id = ? AND version = ?
        // 返回影响的行数 (1 表示成功，0 表示失败)
        public int updateStockOptimistic(String productId, int currentVersion) {
            System.out.println("【乐观锁路线】尝试核对版本号并更新...");
            // 真实环境下如果 version 匹配则返回 1，这里我们模拟成功返回 1
            return 1; 
        }
    }

    private DatabaseMapper mapper = new DatabaseMapper();

    /**
     * 第一部分：【悲观锁】防超卖
     * 思想：进厕所直接把门反锁。外面 100 个人必须排队等我用完。
     * 
     * 🧠 逻辑清单：
     * 1. 必须使用 getProductForUpdate 查数据（数据库层面加了排他锁）。
     * 2. 检查 stock 是否 > 0。如果不大于 0，说明没货了，返回 false。
     * 3. 如果有货，计算新库存，调用普通的 updateStock 覆盖更新。
     * 4. 返回 true。
     */
    @Transactional
    public boolean buyWithPessimisticLock(String productId) {
        // TODO: 请在这里手写悲观锁防超卖逻辑
        ProductEntity entity=mapper.getProduct(productId);
        if(entity.stock<=0){
            return false;
        }
        mapper.updateStock(productId,entity.stock-1);
        return true;
    }

    /**
     * 第二部分：【乐观锁】防超卖
     * 思想：像多人协作编辑腾讯文档。谁先点“保存”谁成功，慢的人会报错“文档已过期”。
     * 
     * 🧠 逻辑清单：
     * 1. 使用普通的 getProduct 查询出数据（也就是拿到了当前库存，和极其重要的【当前版本号】）。
     * 2. 检查 stock 是否 > 0。如果不大于 0，返回 false。
     * 3. 【核心防线】不加任何排队锁！直接调用 updateStockOptimistic() 扣减库存，记得把刚才查到的 version 传进去！
     * 4. 检查更新影响的行数：如果是 1，说明这期间没人改过数据，购买成功 return true；如果是 0，说明别人抢先改了版本号，购买失败 return false。
     */
    public boolean buyWithOptimisticLock(String productId) {
        // TODO: 请在这里手写乐观锁防超卖逻辑
        ProductEntity entity=mapper.getProduct(productId);
        if(entity.stock<=0){
            return false;
        }
        int rows=mapper.updateStockOptimistic(productId,entity.version);
        if(rows==0){
            return true;
        }else{
            return false;
        }

    }
}
