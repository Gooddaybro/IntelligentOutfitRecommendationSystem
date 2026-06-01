package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import java.time.LocalDate;

/**
 * 用户基础资料响应，返回前端资料页展示所需的账号画像字段。
 */
public record UserProfileResponse(
        Long userId,
        String nickname,
        String avatarUrl,
        String gender,
        LocalDate birthday
) {
}
