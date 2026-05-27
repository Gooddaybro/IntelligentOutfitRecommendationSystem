package com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.LoginLog;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.RefreshTokenRecord;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserAuthMapper {

    int existsByUsername(@Param("username") String username);

    int existsByPhone(@Param("phone") String phone);

    int existsByEmail(@Param("email") String email);

    void insertUserAccount(UserAccount userAccount);

    Long findRoleIdByCode(@Param("code") String code);

    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    UserAccount findByUsername(@Param("username") String username);

    UserAccount findById(@Param("userId") Long userId);

    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    void insertRefreshToken(RefreshTokenRecord refreshToken);

    RefreshTokenRecord findRefreshTokenByHash(@Param("tokenHash") String tokenHash);

    void revokeRefreshToken(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);

    void markRefreshTokenUsed(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    void insertLoginLog(LoginLog loginLog);
}
