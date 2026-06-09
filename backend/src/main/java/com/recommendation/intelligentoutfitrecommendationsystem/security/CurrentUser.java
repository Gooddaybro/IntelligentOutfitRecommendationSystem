package com.recommendation.intelligentoutfitrecommendationsystem.security;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Controller 层使用的当前用户快照。
 *
 * 画像接口只从 JWT subject 解析 userId，不接受请求参数中的 userId，
 * 防止用户通过改参数横向读取或修改他人的画像数据。
 */
public record CurrentUser(
        Long userId,
        String username,
        List<String> roles
) {
    public static CurrentUser from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadRequestException("current user is unavailable");
        }
        Long userId = Long.valueOf(jwt.getSubject());
        String username = jwt.getClaimAsString("username");
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new CurrentUser(userId, username, roles == null ? List.of() : roles);
    }
}
