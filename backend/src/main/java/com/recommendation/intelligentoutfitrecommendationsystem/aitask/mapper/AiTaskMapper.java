package com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.AiTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * AI 任务状态原子更新边界，所有租约和终态转换都由受条件约束的 SQL 完成。
 */
@Mapper
public interface AiTaskMapper {

    int insertTask(AiTask task);

    AiTask findByTaskId(@Param("taskId") String taskId);

    AiTask findActiveTask(@Param("taskType") String taskType);

    int claimTask(
            @Param("taskId") String taskId,
            @Param("expectedVersion") long expectedVersion,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    int markRetryWait(
            @Param("taskId") String taskId,
            @Param("expectedVersion") long expectedVersion,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary
    );

    int markSuccessAndClearActiveSlot(
            @Param("taskId") String taskId,
            @Param("expectedVersion") long expectedVersion,
            @Param("resultJson") String resultJson
    );

    int markFailedAndClearActiveSlot(
            @Param("taskId") String taskId,
            @Param("expectedVersion") long expectedVersion,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary
    );
}
