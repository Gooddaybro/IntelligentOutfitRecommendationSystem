package com.recommendation.intelligentoutfitrecommendationsystem.conversation.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatMessage;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话与消息数据访问入口，维护用户隔离的聊天会话历史。
 */
@Mapper
public interface ConversationMapper {
    void insertSession(ChatSession session);

    ChatSession findSessionByThreadIdAndUserId(@Param("threadId") String threadId, @Param("userId") Long userId);

    List<ChatSession> findSessionsByUserId(@Param("userId") Long userId);

    void archiveSession(@Param("threadId") String threadId, @Param("userId") Long userId);

    void insertMessage(ChatMessage message);

    List<ChatMessage> findMessagesBySessionId(@Param("sessionId") Long sessionId);

    void touchSessionLastMessageAt(@Param("sessionId") Long sessionId);
}
