package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 用户基础资料保存入参，承载昵称、头像、性别和生日等可编辑画像字段。
 */
public record UserProfileRequest(
        @Size(max = 64) String nickname,
        @Size(max = 512) String avatarUrl,
        @Size(max = 32) String gender,
        LocalDate birthday
) {
}
