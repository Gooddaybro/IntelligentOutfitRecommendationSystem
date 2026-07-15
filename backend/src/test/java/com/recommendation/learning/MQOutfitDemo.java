package com.recommendation.learning;

public class MQOutfitDemo {

    // 模拟 MQ 生产者（发消息）
    static class MQProducer {
        public void send(String topic, String message) {
            System.out.println("向 MQ 发送消息 -> Topic: " + topic + ", 内容: " + message);
        }
    }

    // 模拟底层数据库
    static class Database {
        public void updateOutfitResult(String taskId, String result) {
            System.out.println("数据库更新 -> 任务 " + taskId + " 结果已保存: " + result);
        }
    }

    // 模拟调用耗时的外部 AI 模型
    static class AIClient {
        public String generateOutfit(String userId) {
            System.out.println("AI 正在疯狂计算中，请等待 5 秒...");
            try { Thread.sleep(5000); } catch (Exception e) {} // 模拟耗时 5 秒
            return "白衬衫 + 黑西装";
        }
    }

    private MQProducer mqProducer = new MQProducer();
    private Database db = new Database();
    private AIClient aiClient = new AIClient();

    /**
     * 核心任务 第一部分：模拟 Controller 层接收前端请求
     * 场景：用户点击了“一键生成穿搭”。
     * 要求：绝不能在这里同步等待 AI！发个消息给 MQ 就立刻安抚前端。
     */
    public String generateOutfitApi(String userId) {
        String taskId = "TASK_" + System.currentTimeMillis();

        // TODO: 1. 组装消息（简单的拼接一下就行，比如 userId + "," + taskId）
        String message=userId+","+taskId;
        // TODO: 2. 调用 mqProducer，将消息发给主题 "outfit_generate_topic"
        mqProducer.send("outfit_generate_topic", message);
        // TODO: 3. 立刻 return 告诉前端："您的穿搭正在生成中，请稍后在消息中心查看。任务ID：" + taskId
        return "您的穿搭正在生成中，请稍后在消息中心查看。任务ID："+taskId;
    }

    /**
     * 核心任务 第二部分：模拟 MQ 消费者（监听者）
     * 场景：MQ 会自动把刚刚发的消息推送到这个方法里。
     * 要求：在这里真正执行耗时任务，并在完成后更新数据库。
     */
    public void onMessageReceived(String message) {
        // TODO: 1. 从 message 中拆解出 userId 和 taskId (可以用 message.split(","))
        String[] messageArray = message.split(",");
        String userId = messageArray[0];
        String taskId = messageArray[1];
        // TODO: 2. 调用 aiClient.generateOutfit(userId) 获取穿搭结果
        String output=aiClient.generateOutfit(userId);
        // TODO: 3. 调用 db.updateOutfitResult() 保存到数据库，前端就可以随时来查了
        db.updateOutfitResult(taskId,output);
    }
}
