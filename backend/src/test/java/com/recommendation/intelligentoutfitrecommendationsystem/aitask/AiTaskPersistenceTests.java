package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.AiTaskMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.AiTask;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class AiTaskPersistenceTests {

    @Autowired
    private AiTaskMapper aiTaskMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM ai_task_redrive_audit");
        jdbcTemplate.update("DELETE FROM consumer_inbox");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM ai_task");
    }

    @Test
    void insertsAndFindsTheSingleActiveGlobalRebuild() {
        AiTask task = newTask("task-one");

        assertThat(aiTaskMapper.insertTask(task)).isOne();
        assertThat(aiTaskMapper.findActiveTask(AiTaskType.RAG_REBUILD.name()))
                .extracting(AiTask::getTaskId, AiTask::getStatus)
                .containsExactly("task-one", AiTaskStatus.PENDING.name());
    }

    @Test
    void databaseRejectsTwoConcurrentTasksForTheGlobalActiveSlot() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> insert = () -> {
                ready.countDown();
                start.await();
                try {
                    aiTaskMapper.insertTask(newTask("task-" + UUID.randomUUID()));
                    return true;
                } catch (DuplicateKeyException exception) {
                    return false;
                }
            };
            List<Future<Boolean>> results = List.of(executor.submit(insert), executor.submit(insert));
            ready.await();
            start.countDown();

            assertThat(results).extracting(Future::get).containsExactlyInAnyOrder(true, false);
        }
    }

    private AiTask newTask(String taskId) {
        AiTask task = new AiTask();
        task.setTaskId(taskId);
        task.setTaskType(AiTaskType.RAG_REBUILD.name());
        task.setCreatedBy(1L);
        task.setStatus(AiTaskStatus.PENDING.name());
        task.setActiveSlot("GLOBAL_RAG_REBUILD");
        return task;
    }
}
