package com.recommendation.intelligentoutfitrecommendationsystem.admin.service;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAuditLogResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminAuditMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only service boundary for admin audit history exposed by the admin console.
 */
@Service
public class AdminAuditLogService {
    private static final int MAX_LOGS = 200;

    private final AdminAuditMapper adminAuditMapper;

    public AdminAuditLogService(AdminAuditMapper adminAuditMapper) {
        this.adminAuditMapper = adminAuditMapper;
    }

    /**
     * Applies the admin-console audit history cap before delegating to persistence.
     *
     * @return newest audit rows shown by the admin console
     */
    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> listAuditLogs() {
        return adminAuditMapper.findAuditLogs(MAX_LOGS);
    }
}
