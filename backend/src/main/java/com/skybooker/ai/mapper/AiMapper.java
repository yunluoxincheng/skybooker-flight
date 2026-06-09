package com.skybooker.ai.mapper;

import com.skybooker.ai.entity.AiChatMessage;
import com.skybooker.ai.entity.AiChatSession;
import com.skybooker.ai.entity.AiRecommendationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiMapper {

    void insertSession(AiChatSession session);

    AiChatSession findSessionByPublicId(@Param("publicSessionId") String publicSessionId);

    void updateSessionStatus(@Param("id") Long id, @Param("status") String status);

    void insertMessage(AiChatMessage message);

    List<AiChatMessage> findMessagesBySessionId(@Param("sessionId") Long sessionId);

    AiChatMessage findLatestAssistantMessage(@Param("sessionId") Long sessionId);

    void insertRecommendationRecord(AiRecommendationRecord record);

    boolean existsSessionByPublicId(@Param("publicSessionId") String publicSessionId);
}
