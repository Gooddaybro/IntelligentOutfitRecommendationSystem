package com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAuditLogResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminAuditEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis boundary for admin audit persistence so audit SQL stays outside services and controller contracts.
 */
@Mapper
public interface AdminAuditMapper {
    /**
     * Persists an admin audit row inside the caller's business transaction.
     *
     * @param entry normalized audit facts from the admin service boundary
     * @return affected row count reported by the database
     */
    int insertAuditLog(AdminAuditEntry entry);

    /**
     * Loads the newest audit rows for the admin console without exposing raw database access to controllers.
     *
     * @param limit maximum number of rows returned by the query
     * @return newest audit rows ordered by creation time and id
     */
    List<AdminAuditLogResponse> findAuditLogs(@Param("limit") int limit);
}
