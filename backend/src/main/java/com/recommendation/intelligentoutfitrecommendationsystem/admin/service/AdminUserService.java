package com.recommendation.intelligentoutfitrecommendationsystem.admin.service;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminUserResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminUserStatusRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminAuditMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminUserMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminAuditEntry;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Service boundary for admin user management, preserving account validation and audit consistency outside SQL mappers.
 */
@Service
public class AdminUserService {
    private static final String DEFAULT_OPERATOR = "admin";

    private final AdminUserMapper adminUserMapper;
    private final AdminAuditMapper adminAuditMapper;

    public AdminUserService(AdminUserMapper adminUserMapper, AdminAuditMapper adminAuditMapper) {
        this.adminUserMapper = adminUserMapper;
        this.adminAuditMapper = adminAuditMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return adminUserMapper.findUsers();
    }

    /**
     * Changes account availability using the API status vocabulary while recording the admin audit in one transaction.
     *
     * @param userId account id from the route; must be positive to avoid ambiguous writes
     * @param request API payload carrying ACTIVE or DISABLED in a case-insensitive form
     * @return reloaded admin projection after the database stores the normalized status
     */
    @Transactional
    public AdminUserResponse changeUserStatus(Long userId, AdminUserStatusRequest request) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
        String dbStatus = toDbUserStatus(request == null ? null : request.status());
        int updated = adminUserMapper.updateUserStatus(userId, dbStatus);
        if (updated == 0) {
            throw new ResourceNotFoundException("user not found");
        }
        AdminUserResponse user = adminUserMapper.findUserById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("user not found");
        }
        adminAuditMapper.insertAuditLog(new AdminAuditEntry(
                DEFAULT_OPERATOR,
                "disabled".equals(dbStatus) ? "DISABLE_USER" : "ENABLE_USER",
                "USER",
                String.valueOf(userId),
                "SUCCESS",
                user.username()
        ));
        return user;
    }

    private String toDbUserStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("status is required");
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> "active";
            case "DISABLED" -> "disabled";
            default -> throw new BadRequestException("unsupported user status");
        };
    }
}
