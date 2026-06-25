package com.recommendation.intelligentoutfitrecommendationsystem.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 这是一个小型的 Demo 控制器，用于演示如何在现有的 Spring Boot 项目中添加新功能。
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    /**
     * 第一个测试接口
     * 当你在浏览器访问 http://localhost:8080/api/demo/hello 时，就会触发这个方法
     */
    @GetMapping("/hello")
    public String sayHello() {
        return "Hello! 这是一个在推荐系统中新添加的测试接口。你的环境已经跑通啦！";
    }
}
