package com.recommendation.learning;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MQ 标准版练习题
 * 请在 TODO 处补充你的核心逻辑！
 */
public class StandardMQPractice {

    // ==========================================
    // 基础组件和数据结构已为你准备好，无需修改这部分
    // ==========================================
    
    public static class OutfitMessage {
        private String userId;
        private String taskId;

        public OutfitMessage() {}
        public OutfitMessage(String userId, String taskId) {
            this.userId = userId;
            this.taskId = taskId;
        }
        public String getUserId() { return userId; }
        public String getTaskId() { return taskId; }
    }

    private MQProducer mqProducer = new MQProducer();
    private Database db = new Database();
    private AIClient aiClient = new AIClient();
    private ObjectMapper objectMapper = new ObjectMapper();

    // ==========================================
    // 你的答题区域 👇
    // ==========================================

    /**
     * 【生产者端】
     * 场景：用户点击了“一键生成穿搭”。
     * 要求：
     * 1. 生产消息前，在数据库创建任务并标记为 PROCESSING
     * 2. 将消息对象 OutfitMessage 转为 JSON 字符串，发送到 "outfit_topic"
     * 3. 加上 try-catch 异常处理，如果发送失败则在库中更新状态为 FAILED
     */
    public String generateOutfitApi(String userId) {
        String taskId = "TASK_" + System.currentTimeMillis();
        
        // TODO: 1. 在数据库中创建一个状态为 "PROCESSING" 的任务记录 (调用 db.createTask)

        try {
            // TODO: 2. 构造消息对象并转成 JSON 字符串，发送到 MQ (调用 objectMapper 和 mqProducer)

            
        } catch (Exception e) {
            // TODO: 3. 异常兜底，打印错误并更新数据库任务状态为 "FAILED" (调用 db.updateTaskStatus)
            System.err.println("提交任务失败: " + e.getMessage());
            
            return "系统繁忙，提交失败，请稍后再试。";
        }
        
        return "您的穿搭正在生成中，请稍后在消息中心查看。任务ID:" + taskId;
    }

    /**
     * 【消费者端】
     * 场景：MQ 推送消息过来了
     * 要求：
     * 1. 将 JSON 字符串反序列化为 OutfitMessage 对象
     * 2. 调用 AI 接口生成穿搭
     * 3. 成功后更新数据库为 SUCCESS 并保存结果
     * 4. 加上 try-catch 兜底，失败更新为 FAILED
     */
    public void onMessageReceived(String message) {
        String taskId = null;
        try {
            // TODO: 1. 将接收到的 JSON 消息 message 反序列化为 OutfitMessage 对象
            

            // TODO: 2. 获取 userId 和 taskId，调用 AIClient 生成穿搭结果
            

            // TODO: 3. 更新数据库，记录穿搭结果，状态变为 "SUCCESS" (调用 db.updateOutfitResult)
            
            
        } catch (Exception e) {
            // TODO: 4. 异常兜底：如果 taskId 不为空，把数据库里的任务状态更新为 "FAILED"
            System.err.println("消费 MQ 消息出现异常: " + e.getMessage());
            
        }
    }


    // ==========================================
    // 模拟的底层依赖 (无需修改)
    // ==========================================
    static class MQProducer {
        public void send(String topic, String message) throws Exception {
            System.out.println("[MQ-Mock] 发送消息至 " + topic + " -> " + message);
            // 模拟部分概率发送失败，考验你的异常处理
            if (Math.random() < 0.1) throw new RuntimeException("MQ 服务短暂不可用");
        }
    }

    static class Database {
        public void createTask(String taskId, String userId, String status) {
            System.out.println("[DB-Mock] 创建任务 " + taskId + " , 用户: " + userId + " , 状态: " + status);
        }
        public void updateTaskStatus(String taskId, String status) {
            System.out.println("[DB-Mock] 更新任务 " + taskId + " 状态为: " + status);
        }
        public void updateOutfitResult(String taskId, String result, String status) {
            System.out.println("[DB-Mock] 任务 " + taskId + " 结果保存: " + result + " , 最终状态: " + status);
        }
    }

    static class AIClient {
        public String generateOutfit(String userId) throws Exception {
            System.out.println("[AI-Mock] AI 开始思考...");
            Thread.sleep(1000); // 模拟耗时
            // 模拟部分概率 AI 挂了，考验你的异常兜底
            if (Math.random() < 0.1) throw new RuntimeException("AI 模型接口超时");
            return "白衬衫 + 黑西装";
        }
    }
}
