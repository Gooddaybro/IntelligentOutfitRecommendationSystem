package com.recommendation.intelligentoutfitrecommendationsystem.user.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户基础资料持久化模型，保存前端资料页可编辑的公开画像字段。
 */
@Data
public class UserProfile {

    private Long id;

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private String gender;

    private LocalDate birthday;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
