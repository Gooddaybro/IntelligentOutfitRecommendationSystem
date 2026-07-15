package com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员重放审计边界，每次 Redrive 都关联旧事件、新事件和操作者。
 */
@Mapper
public interface AiTaskRedriveAuditMapper {

    int insertRedriveAudit(
            @Param("taskId") String taskId,
            @Param("previousEventId") String previousEventId,
            @Param("newEventId") String newEventId,
            @Param("redrivenBy") long redrivenBy,
            @Param("reason") String reason
    );
}
