package com.recommendation.intelligentoutfitrecommendationsystem.aitask.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.client.RagRebuildClient;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.client.RagRebuildResult;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.AiTaskMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.ConsumerInboxMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging.AiTaskMessage;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.AiTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Worker 执行编排，保证 Python 长调用发生在数据库事务外，终态和 Inbox 原子提交。
 */
@Service
@Profile({"worker", "test"})
public class AiTaskExecutionService {

    /**
     * 决定消费者是否可 ACK，LEASE_BUSY 留给有限重试策略处理。
     */
    public enum ExecutionOutcome {
        SUCCESS,
        DUPLICATE,
        LEASE_BUSY
    }

    private static final String CONSUMER_NAME = "rag-rebuild-worker";
    private static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    private final AiTaskMapper taskMapper;
    private final ConsumerInboxMapper inboxMapper;
    private final RagRebuildClient rebuildClient;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String workerId = "worker-" + UUID.randomUUID();

    @Autowired
    public AiTaskExecutionService(
            AiTaskMapper taskMapper,
            ConsumerInboxMapper inboxMapper,
            RagRebuildClient rebuildClient,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this(taskMapper, inboxMapper, rebuildClient, new TransactionTemplate(transactionManager), clock);
    }

    public AiTaskExecutionService(
            AiTaskMapper taskMapper,
            ConsumerInboxMapper inboxMapper,
            RagRebuildClient rebuildClient,
            TransactionOperations transactions,
            Clock clock
    ) {
        this.taskMapper = taskMapper;
        this.inboxMapper = inboxMapper;
        this.rebuildClient = rebuildClient;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * 执行一条有效事件；成功返回前，任务终态和 Inbox 已在同一事务中提交。
     */
    public ExecutionOutcome execute(AiTaskMessage message) {
        validate(message);
        if (inboxMapper.exists(CONSUMER_NAME, message.eventId())) {
            return ExecutionOutcome.DUPLICATE;
        }

        AiTask task = taskMapper.findByTaskId(message.taskId());
        if (task == null) {
            throw new IllegalArgumentException("AI task does not exist");
        }
        if (AiTaskStatus.SUCCESS.name().equals(task.getStatus())
                || AiTaskStatus.FAILED.name().equals(task.getStatus())) {
            return ExecutionOutcome.DUPLICATE;
        }

        long expectedVersion = task.getVersion() == null ? 0L : task.getVersion();
        LocalDateTime now = now();
        int claimed = taskMapper.claimTask(
                task.getTaskId(), expectedVersion, workerId, now, now.plus(LEASE_DURATION)
        );
        if (claimed != 1) {
            return ExecutionOutcome.LEASE_BUSY;
        }

        RagRebuildResult result = rebuildClient.rebuild(
                task.getTaskId(), message.correlationId(), message.traceparent()
        );
        String resultJson = serialize(result);
        transactions.executeWithoutResult(status -> {
            int updated = taskMapper.markSuccessAndClearActiveSlot(
                    task.getTaskId(), expectedVersion + 1, resultJson
            );
            if (updated != 1) {
                throw new IllegalStateException("AI task success state could not be committed");
            }
            inboxMapper.insertInbox(CONSUMER_NAME, message.eventId(), task.getTaskId(), now());
        });
        return ExecutionOutcome.SUCCESS;
    }

    /**
     * 在发布延迟重试前记录 RETRY_WAIT；条件更新失败时让原消息保持未 ACK。
     */
    public void recordRetry(AiTaskMessage message, String failureCode, String failureSummary) {
        AiTask task = requireTask(message.taskId());
        long version = task.getVersion() == null ? 0L : task.getVersion();
        if (taskMapper.markRetryWait(task.getTaskId(), version, failureCode, failureSummary) != 1) {
            throw new IllegalStateException("AI task retry state could not be committed");
        }
    }

    /**
     * 在发布最终 DLQ 前持久化 FAILED 并释放全局活动槽。
     */
    public void recordFailure(AiTaskMessage message, String failureCode, String failureSummary) {
        AiTask task = requireTask(message.taskId());
        long version = task.getVersion() == null ? 0L : task.getVersion();
        if (taskMapper.markFailedAndClearActiveSlot(
                task.getTaskId(), version, failureCode, failureSummary
        ) != 1) {
            throw new IllegalStateException("AI task failure state could not be committed");
        }
    }

    private void validate(AiTaskMessage message) {
        if (message == null
                || message.eventId() == null
                || message.taskId() == null
                || message.schemaVersion() != 1
                || !"ai.task.requested".equals(message.eventType())
                || !AiTaskType.RAG_REBUILD.name().equals(message.taskType())) {
            throw new IllegalArgumentException("invalid AI task message");
        }
    }

    private String serialize(RagRebuildResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("RAG rebuild result could not be serialized", exception);
        }
    }

    private AiTask requireTask(String taskId) {
        AiTask task = taskMapper.findByTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("AI task does not exist");
        }
        return task;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
