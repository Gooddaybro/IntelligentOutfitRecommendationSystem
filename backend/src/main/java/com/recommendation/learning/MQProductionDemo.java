package com.recommendation.learning;

public class MQProductionDemo {

    // 模拟的 JSON 工具类
    static class JSON {
        public static TaskInfo parseObject(String json, Class<?> clazz) {
            // 简单模拟解析 {"userId":"U123", "taskId":"T999"}
            return new TaskInfo("U123", "T999");
        }
    }

    static class TaskInfo {
        private String userId;
        private String taskId;
        public TaskInfo(String userId, String taskId) { this.userId = userId; this.taskId = taskId; }
        public String getUserId() { return userId; }
        public String getTaskId() { return taskId; }
    }

    // 模拟 Redis
    static class Redis {
        public boolean exists(String key) { return false; }
        public void set(String key, String value, int ttlSeconds) {}
    }

    // 模拟 RabbitMQ 的 Channel（通道），用于手动签收
    static class Channel {
        public void basicAck(long deliveryTag) { System.out.println("成功签收 ACK: " + deliveryTag); }
        public void basicNack(long deliveryTag) { System.out.println("拒签 NACK: " + deliveryTag + "，重回队列"); }
    }

    // 模拟 RabbitMQ 原始消息对象
    static class Message {
        private String body;
        private long deliveryTag = 1001L; // 消息的唯一投递编号
        public Message(String body) { this.body = body; }
        public String getBody() { return body; }
        public long getDeliveryTag() { return deliveryTag; }
    }

    static class AIClient {
        public String generateOutfit(String userId) {
            // 模拟极小概率 AI 服务崩溃
            if (Math.random() < 0.1) throw new RuntimeException("AI 网络超时断开！");
            return "白衬衫 + 黑西装";
        }
    }

    static class Database {
        public void updateOutfitResult(String taskId, String result) {}
    }

    private Redis redis = new Redis();
    private AIClient aiClient = new AIClient();
    private Database db = new Database();

    /**
     * 实战任务：手写生产级别带 ACK、幂等性和 JSON 的消费者。
     * 
     * 🧠 逻辑清单（Logic Checklist）：
     * 1. 提取 deliveryTag（用于最终的 ACK 或 NACK）。
     * 2. 开启 try 块。
     * 3. [JSON] 把 message.getBody() 也就是 json 字符串，解析成 TaskInfo 对象（调用 JSON.parseObject）。
     * 4. [幂等性] 检查 Redis 里是否有 "processed:" + taskId，如果有，直接 basicAck() 并 return。
     * 5. [核心业务] 调用 aiClient 生成穿搭，再存入 db。
     * 6. [防重复记录] 业务跑完了，把 "processed:" + taskId 存入 Redis。
     * 7. [可靠性] 调用 basicAck()，通知 MQ 删除消息。
     * 8. 开启 catch 块。
     * 9. [异常回退] 如果进到了 catch，说明 AI 崩了或 DB 挂了，调用 basicNack() 让消息重回队列。
     */
    public void onMessageReceived(Message mqMessage, Channel channel) {
        long deliveryTag = mqMessage.getDeliveryTag();
        String taskId="";
        try{
            String jsons=mqMessage.getBody();
            taskId=JSON.parseObject(jsons,TaskInfo.class).getTaskId();
            String userId=JSON.parseObject(jsons,TaskInfo.class).getUserId();
            if(redis.exists("processed:"+taskId)){
                 channel.basicAck(deliveryTag);
                return;
            }
            String fit=aiClient.generateOutfit(userId);
            db.updateOutfitResult(taskId,fit);
            redis.set("processed"+taskId,"done",60*60);
            channel.basicAck(deliveryTag);
            return;
        }catch(Exception e){
            channel.basicNack(deliveryTag);
        }
    }
}
