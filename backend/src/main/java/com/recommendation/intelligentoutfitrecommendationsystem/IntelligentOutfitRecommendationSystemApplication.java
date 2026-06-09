package com.recommendation.intelligentoutfitrecommendationsystem;

import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderTimeoutProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 应用启动入口，加载智能穿搭推荐后端的 Web、数据访问、安全配置和订单超时任务。
 */
@EnableScheduling
@EnableConfigurationProperties(OrderTimeoutProperties.class)
@SpringBootApplication
public class IntelligentOutfitRecommendationSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligentOutfitRecommendationSystemApplication.class, args);
    }

}
