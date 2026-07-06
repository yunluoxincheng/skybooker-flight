package com.skybooker.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.entity.AiChatMessage;
import com.skybooker.ai.entity.AiChatSession;
import com.skybooker.ai.entity.AiRecommendationRecord;
import com.skybooker.ai.enums.AiReplyType;
import com.skybooker.ai.enums.MessageType;
import com.skybooker.ai.enums.SessionStatus;
import com.skybooker.ai.mapper.AiMapper;
import com.skybooker.ai.orchestrator.AssistantTurnResult;
import com.skybooker.ai.orchestrator.AiAssistantOrchestrator;
import com.skybooker.ai.orchestrator.RecommendationLog;
import com.skybooker.ai.ratelimit.AiRateLimiter;
import com.skybooker.ai.vo.AiChatReplyVO;
import com.skybooker.ai.vo.AiSessionMessageVO;
import com.skybooker.ai.vo.AiSessionMessagesVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final int MAX_PUBLIC_ID_RETRIES = 10;

    private final AiMapper aiMapper;
    private final ObjectMapper objectMapper;
    private final AiRateLimiter aiRateLimiter;
    private final AiAssistantOrchestrator aiAssistantOrchestrator;

    @Transactional
    public AiChatReplyVO chat(String sessionId, String message, String clientIp) {
        if (!aiRateLimiter.tryAcquire(clientIp)) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        AiChatSession session = resolveSession(sessionId, currentUserId);

        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("USER");
        userMsg.setContent(message);
        userMsg.setMessageType(MessageType.TEXT.name());
        aiMapper.insertMessage(userMsg);

        List<AiChatMessage> history = aiMapper.findMessagesBySessionId(session.getId());
        AssistantTurnResult turn = aiAssistantOrchestrator.handle(session, message, history);
        AiChatReplyVO reply = turn.reply();

        AiChatMessage assistantMsg = new AiChatMessage();
        assistantMsg.setSessionId(session.getId());
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(reply.getReplyText());
        assistantMsg.setMessageType(AiReplyType.FLIGHT_RECOMMENDATION.name().equals(reply.getReplyType())
                ? MessageType.RECOMMENDATION.name() : MessageType.TEXT.name());
        assistantMsg.setExtraJson(toJson(turn.extraJson()));
        aiMapper.insertMessage(assistantMsg);

        persistRecommendationIfNeeded(session, turn.recommendationLog());
        return reply;
    }

    public AiSessionMessagesVO getSessionMessages(String sessionId) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        AiChatSession session = loadAndAuthorizeSession(sessionId, currentUserId);
        if (SessionStatus.DELETED.name().equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<AiChatMessage> messages = aiMapper.findMessagesBySessionId(session.getId());
        List<AiSessionMessageVO> voMessages = messages.stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());

        return AiSessionMessagesVO.builder()
                .sessionId(session.getPublicSessionId())
                .status(session.getStatus())
                .messages(voMessages)
                .build();
    }

    @Transactional
    public void deleteSession(String sessionId) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        AiChatSession session = loadAndAuthorizeSession(sessionId, currentUserId);
        aiMapper.updateSessionStatus(session.getId(), SessionStatus.DELETED.name());
    }

    private AiChatSession resolveSession(String sessionId, Long currentUserId) {
        if (sessionId != null && !sessionId.isBlank()) {
            AiChatSession session = loadAndAuthorizeSession(sessionId, currentUserId);
            if (SessionStatus.DELETED.name().equals(session.getStatus())) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return session;
        }
        return createSession(currentUserId);
    }

    private AiChatSession createSession(Long userId) {
        AiChatSession session = new AiChatSession();
        session.setPublicSessionId(generatePublicSessionId());
        session.setUserId(userId);
        session.setSessionTitle("AI航班助手对话");
        session.setStatus(SessionStatus.ACTIVE.name());
        aiMapper.insertSession(session);
        return session;
    }

    private String generatePublicSessionId() {
        for (int i = 0; i < MAX_PUBLIC_ID_RETRIES; i++) {
            String id = UUID.randomUUID().toString().replace("-", "");
            if (!aiMapper.existsSessionByPublicId(id)) {
                return id;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR);
    }

    private AiChatSession loadAndAuthorizeSession(String publicSessionId, Long currentUserId) {
        AiChatSession session = aiMapper.findSessionByPublicId(publicSessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (currentUserId != null) {
            if (session.getUserId() == null || !currentUserId.equals(session.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        } else if (session.getUserId() != null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    private void persistRecommendationIfNeeded(AiChatSession session, RecommendationLog recommendationLog) {
        if (recommendationLog == null) {
            return;
        }
        AiRecommendationRecord record = new AiRecommendationRecord();
        record.setSessionId(session.getId());
        record.setUserId(session.getUserId());
        record.setQueryText(recommendationLog.queryText());
        record.setParsedConditionJson(toJson(recommendationLog.parsedCondition()));
        record.setRecommendedFlightIds(recommendationLog.flights().stream()
                .map(flight -> String.valueOf(flight.get("flightId")))
                .collect(Collectors.joining(",")));
        record.setSearchUrl(recommendationLog.searchUrl());
        aiMapper.insertRecommendationRecord(record);
    }

    private AiSessionMessageVO toMessageVO(AiChatMessage msg) {
        AiSessionMessageVO vo = new AiSessionMessageVO();
        vo.setRole(msg.getRole());
        vo.setContent(msg.getContent());
        vo.setMessageType(msg.getMessageType());
        vo.setCreatedAt(msg.getCreatedAt());
        if (msg.getExtraJson() != null && !msg.getExtraJson().isBlank()) {
            try {
                vo.setExtra(objectMapper.readValue(msg.getExtraJson(),
                        new TypeReference<LinkedHashMap<String, Object>>() {}));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse extra_json for message {}", msg.getId(), e);
            }
        }
        return vo;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON", e);
            return "{}";
        }
    }
}
