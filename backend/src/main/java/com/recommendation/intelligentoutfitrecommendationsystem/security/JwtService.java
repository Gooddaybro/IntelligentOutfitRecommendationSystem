package com.recommendation.intelligentoutfitrecommendationsystem.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 只负责短效 Access JWT 的签发。
 *
 * Refresh Token 是有状态安全凭证，由 AuthService 生成随机值并持久化哈希；
 * 两类令牌分开处理，便于实现退出登录和 refresh token 撤销。
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public String createAccessToken(Long userId, String username, List<String> roles) {
        Instant now = Instant.now();
        // roles 直接写入 JWT，Resource Server 可无状态完成常规接口鉴权。
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getAccessTokenSeconds()))
                .claim("username", username)
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenSeconds() {
        return jwtProperties.getAccessTokenSeconds();
    }
}
