package com.recommendation.intelligentoutfitrecommendationsystem.aitask.service;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto.AiTaskResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto.CreateAiTaskRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto.RedriveAiTaskRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.AiTaskMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.AiTaskRedriveAuditMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.OutboxEventMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.AiTask;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.OutboxEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 管理员 AI 任务用例边界，保证任务、Outbox 与 Redrive 审计在同一事务中提交。
 */
@Service
public class AiTaskApplicationService {

    private static final String ACTIVE_SLOT = "GLOBAL_RAG_REBUILD";

    private final AiTaskMapper taskMapper;
    private final OutboxEventMapper outboxMapper;
    private final AiTaskRedriveAuditMapper auditMapper;
    private final AiTaskEventFactory eventFactory;
    public AiTaskApplicationService(
            AiTaskMapper taskMapper,
            OutboxEventMapper outboxMapper,
            AiTaskRedriveAuditMapper auditMapper,
            AiTaskEventFactory eventFactory
    ) {
        this.taskMapper = taskMapper;
        this.outboxMapper = outboxMapper;
        this.auditMapper = auditMapper;
        this.eventFactory = eventFactory;
    }

    /**
     * 创建全局任务；已有活动任务或并发唯一键竞争时返回同一 taskId，不重复排队。
     */
    @Transactional
    public AiTaskResponse createTask(
            long createdBy,
            CreateAiTaskRequest request,
            String correlationId,
            String traceparent
    ) {
        AiTask active = taskMapper.findActiveTask(request.taskType().name());
        if (active != null) {
            return toResponse(active, true);
        }

        AiTask task = new AiTask();
        task.setTaskId("task_" + UUID.randomUUID().toString().replace("-", ""));
        task.setTaskType(request.taskType().name());
        task.setCreatedBy(createdBy);
        task.setStatus(AiTaskStatus.PENDING.name());
        task.setActiveSlot(ACTIVE_SLOT);
        try {
            taskMapper.insertTask(task);
        } catch (DuplicateKeyException exception) {
            AiTask winner = taskMapper.findActiveTask(request.taskType().name());
            if (winner != null) {
                return toResponse(winner, true);
            }
            throw exception;
        }

        outboxMapper.insertOutbox(eventFactory.createRequested(task, correlationId, traceparent));
        return toResponse(taskMapper.findByTaskId(task.getTaskId()), false);
    }

    public AiTaskResponse getTask(String taskId) {
        AiTask task = taskMapper.findByTaskId(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("AI task not found: " + taskId);
        }
        return toResponse(task, false);
    }

    /**
     * 只重放 FAILED 任务；恢复活动槽、新 Outbox 事件和审计记录必须原子提交。
     */
    @Transactional
    public AiTaskResponse redrive(
            String taskId,
            long redrivenBy,
            RedriveAiTaskRequest request,
            String correlationId,
            String traceparent
    ) {
        AiTask task = taskMapper.findByTaskId(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("AI task not found: " + taskId);
        }
        if (!AiTaskStatus.FAILED.name().equals(task.getStatus())) {
            throw new BadRequestException("only FAILED AI tasks can be redriven");
        }

        try {
            if (taskMapper.resetFailedForRedrive(taskId, ACTIVE_SLOT) != 1) {
                throw new BadRequestException("AI task is no longer eligible for redrive");
            }
        } catch (DuplicateKeyException exception) {
            AiTask active = taskMapper.findActiveTask(task.getTaskType());
            if (active != null) {
                return toResponse(active, true);
            }
            throw exception;
        }

        AiTask reset = taskMapper.findByTaskId(taskId);
        OutboxEvent previous = outboxMapper.findLatestByAggregateId(taskId);
        OutboxEvent next = eventFactory.createRequested(reset, correlationId, traceparent);
        outboxMapper.insertOutbox(next);
        auditMapper.insertRedriveAudit(
                taskId,
                previous == null ? null : previous.getEventId(),
                next.getEventId(),
                redrivenBy,
                request == null ? null : request.reason()
        );
        return toResponse(reset, false);
    }

    private AiTaskResponse toResponse(AiTask task, boolean replayed) {
        return new AiTaskResponse(
                task.getTaskId(),
                task.getTaskType(),
                task.getStatus(),
                task.getAttemptCount() == null ? 0 : task.getAttemptCount(),
                task.getFailureCode(),
                task.getFailureSummary(),
                task.getResultJson(),
                replayed
        );
    }
}
