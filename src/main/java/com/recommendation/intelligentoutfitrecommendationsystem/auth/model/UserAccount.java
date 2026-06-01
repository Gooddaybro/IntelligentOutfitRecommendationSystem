package com.recommendation.intelligentoutfitrecommendationsystem.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号持久化模型，保存登录标识、密码摘要和账号状态。
 */
@Data
public class UserAccount {

    private Long id;

    private String username;

    private String phone;

    private String email;

    private String passwordHash;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
