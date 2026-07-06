package com.skybooker.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.entity.AiChatMessage;
import com.skybooker.ai.entity.AiChatSession;
import com.skybooker.ai.entity.AiRecommendationRecord;
import com.skybooker.ai.enums.AiReplyType;
import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.enums.MessageType;
import com.skybooker.ai.enums.SessionStatus;
import com.skybooker.ai.mapper.AiMapper;
import com.skybooker.ai.parser.IntentParser;
import com.skybooker.ai.parser.IntentParserService;
import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.ratelimit.AiRateLimiter;
import com.skybooker.ai.vo.AiChatReplyVO;
import com.skybooker.ai.vo.AiSessionMessageVO;
import com.skybooker.ai.vo.AiSessionMessagesVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.mapper.FlightMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final int MAX_PUBLIC_ID_RETRIES = 10;
    private static final String TRAVEL_CONTEXT = "travelContext";
    private static final String DESTINATION_CITY = "destinationCity";

    private final AiMapper aiMapper;
    private final IntentParser intentParser;
    private final IntentParserService ruleIntentParserService;
    private final FlightRecommendationService flightRecommendationService;
    private final DomainIntentRouter domainIntentRouter;
    private final DomainReplyComposer domainReplyComposer;
    private final FlightMapper flightMapper;
    private final ObjectMapper objectMapper;
    private final AiRateLimiter aiRateLimiter;

    @Transactional
    public AiChatReplyVO chat(String sessionId, String message, String clientIp) {
        if (!aiRateLimiter.tryAcquire(clientIp)) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        AiChatSession session;

        if (sessionId != null && !sessionId.isBlank()) {
            session = loadAndAuthorizeSession(sessionId, currentUserId);
            if (SessionStatus.DELETED.name().equals(session.getStatus())) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
        } else {
            session = createSession(currentUserId);
        }

        // Persist user message
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("USER");
        userMsg.setContent(message);
        userMsg.setMessageType(MessageType.TEXT.name());
        aiMapper.insertMessage(userMsg);

        AiChatMessage previousAssistant = aiMapper.findLatestAssistantMessage(session.getId());
        DomainIntentRouter.RouteResult route = domainIntentRouter.route(message, previousAssistant);

        AiChatReplyVO reply;
        if (route.intent() == DomainIntent.FLIGHT_QUERY || route.intent() == DomainIntent.FLIGHT_QUERY_CONTINUATION) {
            ParsedCondition condition = intentParser.parse(message);
            condition = mergeWithTravelContext(previousAssistant, condition);
            if (route.intent() == DomainIntent.FLIGHT_QUERY_CONTINUATION) {
                condition = mergeWithPreviousContext(previousAssistant, condition);
            }

            if (!condition.isComplete()) {
                reply = buildFollowUpReply(session.getPublicSessionId(), condition, route.intent());
            } else {
                condition = normalizeForSearch(condition);
                Long resolvedAirlineId = resolveAirlineId(condition.getAirlineRaw());
                List<Map<String, Object>> flights = flightRecommendationService.recommend(condition, resolvedAirlineId);

                if (flights.isEmpty()) {
                    reply = buildNoResultReply(session.getPublicSessionId(), condition, route.intent());
                } else {
                    reply = buildRecommendationReply(session.getPublicSessionId(), condition, flights,
                            message, route.intent());
                }
            }
        } else {
            reply = buildConversationalReply(session.getPublicSessionId(), message, route);
        }

        // Persist assistant message
        AiChatMessage assistantMsg = new AiChatMessage();
        assistantMsg.setSessionId(session.getId());
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(reply.getReplyText());
        assistantMsg.setMessageType(
                AiReplyType.FLIGHT_RECOMMENDATION.name().equals(reply.getReplyType())
                        ? MessageType.RECOMMENDATION.name() : MessageType.TEXT.name());
        assistantMsg.setExtraJson(toJson(buildExtraJson(reply, message)));
        aiMapper.insertMessage(assistantMsg);

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
        } else {
            if (session.getUserId() != null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        return session;
    }

    @SuppressWarnings("unchecked")
    private ParsedCondition mergeWithTravelContext(AiChatMessage prevAssistant, ParsedCondition current) {
        if (current.getArrivalCity() != null || prevAssistant == null || prevAssistant.getExtraJson() == null) {
            return current;
        }

        try {
            Map<String, Object> extra = objectMapper.readValue(
                    prevAssistant.getExtraJson(), new TypeReference<LinkedHashMap<String, Object>>() {});
            Object travelContext = extra.get(TRAVEL_CONTEXT);
            if (!(travelContext instanceof Map<?, ?> context)) {
                return current;
            }
            Object destinationCity = context.get(DESTINATION_CITY);
            if (!(destinationCity instanceof String city) || city.isBlank()) {
                return current;
            }
            ParsedCondition merged = current.toBuilder().arrivalCity(city).build();
            return withRecomputedRequiredFields(merged, current.getFollowUpQuestion());
        } catch (Exception e) {
            log.warn("Failed to parse travel context for merging", e);
            return current;
        }
    }

    @SuppressWarnings("unchecked")
    private ParsedCondition mergeWithPreviousContext(AiChatMessage prevAssistant, ParsedCondition current) {
        if (prevAssistant == null || prevAssistant.getExtraJson() == null) return current;

        Map<String, Object> prevCondition;
        try {
            Map<String, Object> extra = objectMapper.readValue(
                    prevAssistant.getExtraJson(), new TypeReference<LinkedHashMap<String, Object>>() {});
            Object missingFields = extra.get("missingFields");
            if (!AiReplyType.FOLLOW_UP.name().equals(extra.get("replyType"))
                    || !(missingFields instanceof List<?> missing)
                    || missing.isEmpty()) {
                return current;
            }
            prevCondition = (Map<String, Object>) extra.get("parsedCondition");
        } catch (Exception e) {
            log.warn("Failed to parse previous context for merging", e);
            return current;
        }

        if (prevCondition == null) return current;

        ParsedCondition.ParsedConditionBuilder merged = current.toBuilder();
        // 必填字段
        if (current.getDepartureCity() == null && prevCondition.get("departureCity") != null)
            merged.departureCity((String) prevCondition.get("departureCity"));
        if (current.getArrivalCity() == null && prevCondition.get("arrivalCity") != null)
            merged.arrivalCity((String) prevCondition.get("arrivalCity"));
        if (current.getDepartureDate() == null && prevCondition.get("departureDate") != null) {
            merged.departureDate(LocalDate.parse((String) prevCondition.get("departureDate")));
        }
        if (current.getDepartureDateStart() == null && prevCondition.get("departureDateStart") != null) {
            merged.departureDateStart(LocalDate.parse((String) prevCondition.get("departureDateStart")));
        }
        if (current.getDepartureDateEnd() == null && prevCondition.get("departureDateEnd") != null) {
            merged.departureDateEnd(LocalDate.parse((String) prevCondition.get("departureDateEnd")));
        }
        // 可选筛选字段：多轮补全时从上一轮继承，避免丢条件（舱位/航司/价格/时段/时长/直飞/排序/人数）。
        // 即使 current 已经补齐必填字段，也要继续继承未被当前轮覆盖的可选条件。
        // passengerCount 未提及为 null，避免“当前默认 1”覆盖上一轮用户明确说的“两个人”。
        if (current.getPassengerCount() == null && prevCondition.get("passengerCount") != null)
            merged.passengerCount(toInt(prevCondition.get("passengerCount")));
        if (current.getCabinClass() == null && prevCondition.get("cabinClass") != null)
            merged.cabinClass((String) prevCondition.get("cabinClass"));
        if (current.getAirlineRaw() == null && prevCondition.get("airlineRaw") != null)
            merged.airlineRaw((String) prevCondition.get("airlineRaw"));
        if (current.getMinPrice() == null && prevCondition.get("minPrice") != null)
            merged.minPrice(toBigDecimal(prevCondition.get("minPrice")));
        if (current.getMaxPrice() == null && prevCondition.get("maxPrice") != null)
            merged.maxPrice(toBigDecimal(prevCondition.get("maxPrice")));
        if (current.getDepartureTimeStart() == null && prevCondition.get("departureTimeStart") != null)
            merged.departureTimeStart(LocalTime.parse((String) prevCondition.get("departureTimeStart")));
        if (current.getDepartureTimeEnd() == null && prevCondition.get("departureTimeEnd") != null)
            merged.departureTimeEnd(LocalTime.parse((String) prevCondition.get("departureTimeEnd")));
        if (current.getMaxDurationMinutes() == null && prevCondition.get("maxDurationMinutes") != null)
            merged.maxDurationMinutes(toInt(prevCondition.get("maxDurationMinutes")));
        if (current.getDirectOnly() == null && prevCondition.get("directOnly") != null)
            merged.directOnly((Boolean) prevCondition.get("directOnly"));
        if (current.getSort() == null && prevCondition.get("sort") != null)
            merged.sort((String) prevCondition.get("sort"));

        ParsedCondition result = merged.build();
        return withRecomputedRequiredFields(result, current.getFollowUpQuestion());
    }

    private ParsedCondition withRecomputedRequiredFields(ParsedCondition condition, String followUpQuestion) {
        List<String> missing = new ArrayList<>();
        if (condition.getDepartureCity() == null) missing.add("departureCity");
        if (condition.getArrivalCity() == null) missing.add("arrivalCity");
        if (!condition.hasDepartureDateCondition()) missing.add("departureDate");
        if (missing.isEmpty()) {
            return condition.toBuilder()
                    .missingFields(Collections.emptyList())
                    .followUpQuestion(null)
                    .quickActionLabels(List.of())
                    .build();
        }

        String question = followUpQuestion;
        if (question == null || question.isBlank()) {
            question = buildFollowUpQuestion(missing);
        }
        return condition.toBuilder()
                .missingFields(missing)
                .followUpQuestion(question)
                .quickActionLabels(List.of())
                .build();
    }

    private ParsedCondition normalizeForSearch(ParsedCondition condition) {
        if (condition.getPassengerCount() != null) {
            return condition;
        }
        return condition.toBuilder().passengerCount(1).build();
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof java.math.BigDecimal bd) return bd;
        if (value instanceof Number n) return java.math.BigDecimal.valueOf(n.doubleValue());
        try {
            return new java.math.BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long resolveAirlineId(String airlineRaw) {
        if (airlineRaw == null || airlineRaw.isBlank()) {
            return null;
        }
        return flightMapper.findAirlineIdByCodeOrName(airlineRaw, airlineRaw);
    }

    private String buildFollowUpQuestion(List<String> missingFields) {
        List<String> parts = new ArrayList<>();
        for (String field : missingFields) {
            switch (field) {
                case "departureCity" -> parts.add("出发城市");
                case "arrivalCity" -> parts.add("目的地城市");
                case "departureDate" -> parts.add("出发日期");
                default -> {
                }
            }
        }
        return "请问您的" + String.join("、", parts) + "是什么？";
    }

    private AiChatReplyVO buildFollowUpReply(String sessionId, ParsedCondition condition, DomainIntent intent) {
        List<Map<String, String>> quickActions = condition.getQuickActionLabels().stream()
                .map(label -> Map.of("label", label, "value", label))
                .collect(Collectors.toList());

        return AiChatReplyVO.builder()
                .sessionId(sessionId)
                .replyType(AiReplyType.FOLLOW_UP.name())
                .intent(intent.name())
                .replyText(condition.getFollowUpQuestion())
                .parsedCondition(conditionToMap(condition))
                .missingFields(condition.getMissingFields())
                .followUpQuestion(condition.getFollowUpQuestion())
                .searchUrl(null)
                .flights(Collections.emptyList())
                .quickActions(quickActions)
                .build();
    }

    private AiChatReplyVO buildRecommendationReply(String sessionId, ParsedCondition condition,
                                                    List<Map<String, Object>> flights,
                                                    String originalQuery, DomainIntent intent) {
        String searchUrl = flightRecommendationService.buildSearchUrl(condition);
        String replyText = "为您找到 " + flights.size() + " 个推荐航班：";

        List<Map<String, String>> quickActions = List.of(
                Map.of("label", "查看全部航班", "value", "查看全部航班"),
                Map.of("label", "换个时间", "value", "换个时间")
        );

        // Persist recommendation record
        AiRecommendationRecord record = new AiRecommendationRecord();
        // We need the session - get it from sessionId
        AiChatSession session = aiMapper.findSessionByPublicId(sessionId);
        record.setSessionId(session.getId());
        record.setUserId(session.getUserId());
        record.setQueryText(originalQuery);
        record.setParsedConditionJson(toJson(conditionToMap(condition)));
        record.setRecommendedFlightIds(flights.stream()
                .map(f -> String.valueOf(f.get("flightId")))
                .collect(Collectors.joining(",")));
        record.setSearchUrl(searchUrl);
        aiMapper.insertRecommendationRecord(record);

        return AiChatReplyVO.builder()
                .sessionId(sessionId)
                .replyType(AiReplyType.FLIGHT_RECOMMENDATION.name())
                .intent(intent.name())
                .replyText(replyText)
                .parsedCondition(conditionToMap(condition))
                .missingFields(Collections.emptyList())
                .followUpQuestion(null)
                .searchUrl(searchUrl)
                .flights(flights)
                .quickActions(quickActions)
                .build();
    }

    private AiChatReplyVO buildNoResultReply(String sessionId, ParsedCondition condition, DomainIntent intent) {
        String replyText = "抱歉，没有找到符合条件的航班。请尝试调整搜索条件。";
        List<Map<String, String>> quickActions = List.of(
                Map.of("label", "换个日期", "value", "换个日期"),
                Map.of("label", "查看全部航班", "value", "查看全部航班")
        );

        return AiChatReplyVO.builder()
                .sessionId(sessionId)
                .replyType(AiReplyType.NO_RESULT.name())
                .intent(intent.name())
                .replyText(replyText)
                .parsedCondition(conditionToMap(condition))
                .missingFields(Collections.emptyList())
                .followUpQuestion("是否需要调整搜索条件？")
                .searchUrl(null)
                .flights(Collections.emptyList())
                .quickActions(quickActions)
                .build();
    }

    private AiChatReplyVO buildConversationalReply(String sessionId, String message, DomainIntentRouter.RouteResult route) {
        DomainIntent intent = route.intent();
        AiReplyType replyType = switch (intent) {
            case TRAVEL_CHAT -> AiReplyType.TRAVEL_CHAT;
            case BOOKING_HELP -> AiReplyType.BOOKING_HELP;
            case OUT_OF_SCOPE -> AiReplyType.OUT_OF_SCOPE;
            case FLIGHT_QUERY, FLIGHT_QUERY_CONTINUATION -> AiReplyType.FOLLOW_UP;
        };

        List<Map<String, String>> quickActions = switch (intent) {
            case TRAVEL_CHAT -> List.of(
                    Map.of("label", "帮我查机票", "value", "帮我查机票"),
                    Map.of("label", "推荐旅行目的地", "value", "有哪些地方推荐去玩")
            );
            case BOOKING_HELP -> List.of(
                    Map.of("label", "查询订单", "value", "订单怎么查看"),
                    Map.of("label", "查机票", "value", "帮我查机票")
            );
            case OUT_OF_SCOPE -> List.of(
                    Map.of("label", "查机票", "value", "帮我查机票"),
                    Map.of("label", "旅行建议", "value", "有哪些地方推荐去玩")
            );
            case FLIGHT_QUERY, FLIGHT_QUERY_CONTINUATION -> List.of();
        };

        return AiChatReplyVO.builder()
                .sessionId(sessionId)
                .replyType(replyType.name())
                .intent(intent.name())
                .replyText(domainReplyComposer.compose(intent, message, route.llmConfig()))
                .parsedCondition(Collections.emptyMap())
                .missingFields(Collections.emptyList())
                .followUpQuestion(null)
                .searchUrl(null)
                .flights(Collections.emptyList())
                .quickActions(quickActions)
                .build();
    }

    private Map<String, Object> conditionToMap(ParsedCondition condition) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (condition.getDepartureCity() != null) map.put("departureCity", condition.getDepartureCity());
        if (condition.getArrivalCity() != null) map.put("arrivalCity", condition.getArrivalCity());
        if (condition.getDepartureDate() != null) map.put("departureDate", condition.getDepartureDate().toString());
        if (condition.getDepartureDateStart() != null) map.put("departureDateStart", condition.getDepartureDateStart().toString());
        if (condition.getDepartureDateEnd() != null) map.put("departureDateEnd", condition.getDepartureDateEnd().toString());
        if (condition.getPassengerCount() != null) map.put("passengerCount", condition.getPassengerCount());
        if (condition.getCabinClass() != null) map.put("cabinClass", condition.getCabinClass());
        if (condition.getAirlineRaw() != null) map.put("airlineRaw", condition.getAirlineRaw());
        if (condition.getMaxPrice() != null) map.put("maxPrice", condition.getMaxPrice());
        if (condition.getDepartureTimeStart() != null) map.put("departureTimeStart", condition.getDepartureTimeStart().toString());
        if (condition.getDepartureTimeEnd() != null) map.put("departureTimeEnd", condition.getDepartureTimeEnd().toString());
        if (condition.getMaxDurationMinutes() != null) map.put("maxDurationMinutes", condition.getMaxDurationMinutes());
        if (condition.getDirectOnly() != null) map.put("directOnly", condition.getDirectOnly());
        if (condition.getSort() != null) map.put("sort", condition.getSort());
        return map;
    }

    private Map<String, Object> buildExtraJson(AiChatReplyVO reply, String message) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("replyType", reply.getReplyType());
        extra.put("intent", reply.getIntent());
        extra.put("parsedCondition", reply.getParsedCondition() == null ? Collections.emptyMap() : reply.getParsedCondition());
        extra.put("missingFields", reply.getMissingFields() == null ? Collections.emptyList() : reply.getMissingFields());
        extra.put("followUpQuestion", reply.getFollowUpQuestion());
        extra.put("searchUrl", reply.getSearchUrl());
        extra.put("flights", reply.getFlights() == null ? Collections.emptyList() : reply.getFlights());
        extra.put("quickActions", reply.getQuickActions() == null ? Collections.emptyList() : reply.getQuickActions());
        Map<String, Object> travelContext = buildTravelContext(reply, message);
        if (!travelContext.isEmpty()) {
            extra.put(TRAVEL_CONTEXT, travelContext);
        }
        return extra;
    }

    private Map<String, Object> buildTravelContext(AiChatReplyVO reply, String message) {
        if (!AiReplyType.TRAVEL_CHAT.name().equals(reply.getReplyType())) {
            return Collections.emptyMap();
        }

        ParsedCondition parsed = ruleIntentParserService.parse(message);
        if (parsed.getArrivalCity() == null || parsed.getArrivalCity().isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put(DESTINATION_CITY, parsed.getArrivalCity());
        return context;
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
