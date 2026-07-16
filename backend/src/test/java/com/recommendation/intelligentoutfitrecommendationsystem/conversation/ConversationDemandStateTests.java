package com.recommendation.intelligentoutfitrecommendationsystem.conversation;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentResolver;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentStateService;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ConversationDemandStateTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(7000);

    @Autowired
    private UserAuthMapper userAuthMapper;
    @Autowired
    private ConversationApplicationService conversationService;
    @Autowired
    private DemandIntentStateService demandIntentStateService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndReplaysOneTransitionPerRequest() {
        Long userId = createUser();
        ConversationResponse conversation = conversationService.createConversation(userId, "需求状态测试");
        MessageResponse message = conversationService.appendMessage(
                userId, conversation.threadId(), "user", "男性", "req-demand-1-" + userId);
        var patch = new DemandIntentResolver().resolvePatch(new com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest(
                conversation.threadId(), "男性", null, null, null, null, null, null, null));
        DemandIntent initial = new com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentMerger()
                .merge(null, patch);

        DemandIntent first = demandIntentStateService.apply(
                userId, conversation.threadId(), message.requestId(), message.id(), patch, initial);
        DemandIntent replay = demandIntentStateService.apply(
                userId, conversation.threadId(), message.requestId(), message.id(), patch, initial);

        assertThat(first.targetGender()).isEqualTo("male");
        assertThat(replay).isEqualTo(first);
        Integer transitions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_demand_transition WHERE request_id = ?",
                Integer.class,
                message.requestId()
        );
        assertThat(transitions).isOne();
    }

    private Long createUser() {
        UserAccount account = new UserAccount();
        account.setUsername("demand_state_user_" + USER_SEQUENCE.incrementAndGet());
        account.setPasswordHash("encoded-password");
        account.setStatus("active");
        userAuthMapper.insertUserAccount(account);
        userAuthMapper.insertUserRole(account.getId(), userAuthMapper.findRoleIdByCode("USER"));
        return account.getId();
    }
}
