package com.recommendation.intelligentoutfitrecommendationsystem.conversation;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.mapper.ConversationMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatMessage;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ConversationMapperTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(5000);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Test
    void insertsSessionAndMessagesForUserTimeline() {
        Long userId = createUser();
        ChatSession session = new ChatSession();
        session.setThreadId("th_mapper_001_" + userId);
        session.setUserId(userId);
        session.setTitle("秋季通勤外套");
        session.setStatus("active");
        conversationMapper.insertSession(session);

        conversationMapper.insertMessage(message(session.getId(), userId, "user", "推荐一件通勤外套"));
        conversationMapper.insertMessage(message(session.getId(), userId, "assistant", "可以看通勤夹克"));

        assertThat(conversationMapper.findSessionByThreadIdAndUserId(session.getThreadId(), userId)).isNotNull();
        assertThat(conversationMapper.findMessagesBySessionId(session.getId()))
                .extracting(ChatMessage::getRole)
                .containsExactly("user", "assistant");
    }

    @Test
    void archiveSessionOnlyAffectsCurrentUser() {
        Long ownerId = createUser();
        Long otherUserId = createUser();
        ChatSession session = new ChatSession();
        session.setThreadId("th_archive_" + ownerId);
        session.setUserId(ownerId);
        session.setTitle("归档测试");
        session.setStatus("active");
        conversationMapper.insertSession(session);

        conversationMapper.archiveSession(session.getThreadId(), otherUserId);

        assertThat(conversationMapper.findSessionByThreadIdAndUserId(session.getThreadId(), ownerId).getStatus())
                .isEqualTo("active");

        conversationMapper.archiveSession(session.getThreadId(), ownerId);

        assertThat(conversationMapper.findSessionsByUserId(ownerId)).extracting(ChatSession::getThreadId)
                .doesNotContain(session.getThreadId());
    }

    private ChatMessage message(Long sessionId, Long userId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setMessageStatus("succeeded");
        message.setRequestId("req-test");
        return message;
    }

    private Long createUser() {
        String username = "conversation_mapper_user_" + USER_SEQUENCE.incrementAndGet();
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash("encoded-password");
        userAccount.setStatus("active");
        userAuthMapper.insertUserAccount(userAccount);

        Long roleId = userAuthMapper.findRoleIdByCode("USER");
        userAuthMapper.insertUserRole(userAccount.getId(), roleId);
        return userAccount.getId();
    }
}
