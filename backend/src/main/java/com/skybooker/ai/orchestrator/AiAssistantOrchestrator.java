package com.skybooker.ai.orchestrator;

import com.skybooker.ai.config.LlmEffectiveConfig;
import com.skybooker.ai.entity.AiChatMessage;
import com.skybooker.ai.entity.AiChatSession;
import com.skybooker.ai.enums.AiReplyType;
import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.parser.IntentParserService;
import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.parser.ParsedConditionMaps;
import com.skybooker.ai.parser.SemanticParseResult;
import com.skybooker.ai.parser.SemanticParser;
import com.skybooker.ai.policy.DialogueAction;
import com.skybooker.ai.policy.DialogueActionType;
import com.skybooker.ai.policy.DialoguePolicy;
import com.skybooker.ai.state.ConversationState;
import com.skybooker.ai.state.ConversationStateService;
import com.skybooker.ai.tool.BookingHelpTool;
import com.skybooker.ai.tool.DestinationRecommendTool;
import com.skybooker.ai.tool.DestinationRecommendation;
import com.skybooker.ai.tool.FlightSearchResult;
import com.skybooker.ai.tool.FlightSearchTool;
import com.skybooker.ai.tool.TravelPlanResult;
import com.skybooker.ai.tool.TravelPlanTool;
import com.skybooker.ai.vo.AiChatReplyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAssistantOrchestrator {

    private final SemanticParser semanticParser;
    private final DialoguePolicy dialoguePolicy;
    private final FlightSearchTool flightSearchTool;
    private final DestinationRecommendTool destinationRecommendTool;
    private final BookingHelpTool bookingHelpTool;
    private final TravelPlanTool travelPlanTool;
    private final ConversationStateService conversationStateService;
    private final IntentParserService ruleIntentParserService;

    public AssistantTurnResult handle(AiChatSession session, String message, List<AiChatMessage> history) {
        ConversationState state = conversationStateService.load(history);
        SemanticParseResult semantic = semanticParser.parse(message, state);
        String explicitDestinationCity = ruleIntentParserService.parseDestinationSwitchCity(message);
        ParsedCondition condition = prepareCondition(message, semantic, state, explicitDestinationCity);
        DialogueAction action = dialoguePolicy.decide(message, semantic.intent(), condition, state);

        DestinationRecommendation destinationRecommendation = null;
        TravelPlanResult travelPlanResult = null;
        RecommendationLog recommendationLog = null;
        AiChatReplyVO reply;

        if (action.type() == DialogueActionType.RECOMMEND_DESTINATION_AND_FOLLOW_UP) {
            destinationRecommendation = destinationRecommendTool.recommend(message);
            condition = withDestination(condition, destinationRecommendation.primaryCity());
            condition = ParsedConditionMaps.recomputeRequiredFields(condition);
            if (hasSearchableCondition(condition)) {
                SearchReply searchReply = searchFlights(session.getPublicSessionId(), condition,
                        message, semantic.intent());
                reply = searchReply.reply();
                recommendationLog = searchReply.recommendationLog();
            } else {
                reply = buildFollowUpReply(session.getPublicSessionId(), condition, semantic.intent(),
                        destinationRecommendation.replyText() + "\n" + condition.getFollowUpQuestion());
            }
        } else {
            switch (action.type()) {
                case SEARCH_FLIGHTS -> {
                    SearchReply searchReply = searchFlights(session.getPublicSessionId(), condition,
                            message, semantic.intent());
                    reply = searchReply.reply();
                    recommendationLog = searchReply.recommendationLog();
                }
                case FOLLOW_UP -> reply = buildFollowUpReply(session.getPublicSessionId(),
                        condition, semantic.intent(), condition.getFollowUpQuestion());
                case RECOMMEND_DESTINATION -> {
                    destinationRecommendation = destinationRecommendTool.recommend(message);
                    reply = buildTravelReply(session.getPublicSessionId(), semantic.intent(),
                            destinationRecommendation.replyText(), destinationRecommendation.quickActions());
                }
                case BOOKING_HELP -> reply = buildConversationalReply(session.getPublicSessionId(),
                        DomainIntent.BOOKING_HELP, bookingHelpTool.answer(message), bookingHelpQuickActions());
                case TRAVEL_PLAN -> {
                    if (isDestinationSwitch(state, explicitDestinationCity)) {
                        travelPlanResult = new TravelPlanResult(
                                "好的，已把目的地切换为" + explicitDestinationCity
                                        + "。需要查机票时，请告诉我出发城市和出发日期。",
                                explicitDestinationCity);
                    } else {
                        travelPlanResult = travelPlanTool.advise(message, semantic.llmConfig());
                    }
                    reply = buildTravelReply(session.getPublicSessionId(), semantic.intent(),
                            travelPlanResult.replyText(), travelQuickActions());
                }
                case OUT_OF_SCOPE -> reply = buildConversationalReply(session.getPublicSessionId(),
                        DomainIntent.OUT_OF_SCOPE, outOfScopeReply(), outOfScopeQuickActions());
                default -> throw new IllegalStateException("Unsupported dialogue action: " + action.type());
            }
        }

        ConversationState nextState = conversationStateService.nextState(state, reply, condition,
                destinationRecommendation, travelPlanResult, explicitDestinationCity);
        Map<String, Object> extraJson = buildExtraJson(reply, nextState);
        return new AssistantTurnResult(reply, extraJson, recommendationLog);
    }

    private ParsedCondition prepareCondition(String message, SemanticParseResult semantic,
                                             ConversationState state, String explicitDestinationCity) {
        if (semantic.condition() == null) {
            return null;
        }

        if (isResetRequest(message)) {
            return ParsedConditionMaps.recomputeRequiredFields(semantic.condition());
        }
        ParsedCondition condition = applyBareSlotFill(message, semantic.condition(), state, explicitDestinationCity);
        condition = withDestination(condition, explicitDestinationCity);
        condition = withDestination(condition, state.getRecommendedDestinationCity());
        ParsedCondition base = state.hasPendingFlightQuery()
                ? state.getPendingFlightCondition() : state.getActiveFlightCondition();
        if (base != null && (semantic.intent() == DomainIntent.FLIGHT_QUERY_CONTINUATION
                || state.hasPendingFlightQuery())) {
            condition = ParsedConditionMaps.mergePending(base, condition);
        }
        condition = applyExplicitClears(message, condition);
        return ParsedConditionMaps.recomputeRequiredFields(condition);
    }

    private ParsedCondition applyExplicitClears(String message, ParsedCondition condition) {
        if (condition == null || message == null) return condition;
        ParsedCondition.ParsedConditionBuilder builder = condition.toBuilder();
        if (message.contains("日期不限") || message.contains("时间不限")) {
            builder.departureDate(null).departureDateStart(null).departureDateEnd(null);
        }
        if (message.contains("航空公司不限") || message.contains("航司不限")) builder.airlineRaw(null);
        if (message.contains("不限制价格") || message.contains("价格不限")) builder.minPrice(null).maxPrice(null);
        return builder.build();
    }

    private boolean hasSearchableCondition(ParsedCondition condition) {
        return condition != null && (condition.getDepartureCity() != null
                || condition.getArrivalCity() != null || condition.hasDepartureDateCondition()
                || condition.getAirlineRaw() != null || condition.getCabinClass() != null);
    }

    private boolean isResetRequest(String message) {
        return message != null && (message.contains("清空条件") || message.contains("换个行程")
                || message.contains("重新查询") || message.contains("重新查一下"));
    }

    private ParsedCondition applyBareSlotFill(String message, ParsedCondition condition,
                                              ConversationState state, String explicitDestinationCity) {
        if (!state.hasPendingFlightQuery()
                || explicitDestinationCity != null
                || condition.getDepartureCity() != null
                || condition.getArrivalCity() != null) {
            return condition;
        }
        String city = ruleIntentParserService.parseFirstKnownDestinationCity(message);
        if (city == null) {
            return condition;
        }
        List<String> missing = state.getPendingMissingFields();
        ParsedCondition.ParsedConditionBuilder builder = condition.toBuilder();
        if (missing.contains("arrivalCity") && !missing.contains("departureCity")) {
            builder.arrivalCity(city);
        } else if (missing.contains("departureCity") && !missing.contains("arrivalCity")) {
            builder.departureCity(city);
        }
        return builder.build();
    }

    private boolean isDestinationSwitch(ConversationState state, String explicitDestinationCity) {
        return state != null
                && state.getRecommendedDestinationCity() != null
                && explicitDestinationCity != null
                && !explicitDestinationCity.equals(state.getRecommendedDestinationCity());
    }

    private ParsedCondition withDestination(ParsedCondition condition, String destinationCity) {
        if (condition == null || condition.getArrivalCity() != null
                || destinationCity == null || destinationCity.isBlank()) {
            return condition;
        }
        return condition.toBuilder().arrivalCity(destinationCity).build();
    }

    private SearchReply searchFlights(String sessionId, ParsedCondition condition,
                                      String originalQuery, DomainIntent intent) {
        ParsedCondition normalized = ParsedConditionMaps.normalizeForSearch(condition);
        FlightSearchResult result = flightSearchTool.search(normalized);
        if (result.flights().isEmpty()) {
            return new SearchReply(buildNoResultReply(sessionId, normalized, intent), null);
        }

        AiChatReplyVO reply = buildRecommendationReply(sessionId, normalized, result, intent);
        RecommendationLog recommendationLog = new RecommendationLog(originalQuery,
                ParsedConditionMaps.toMap(normalized), result.flights(), result.searchUrl());
        return new SearchReply(reply, recommendationLog);
    }

    private AiChatReplyVO buildFollowUpReply(String sessionId, ParsedCondition condition,
                                             DomainIntent intent, String replyText) {
        List<Map<String, String>> quickActions = condition.getQuickActionLabels().stream()
                .map(label -> Map.of("label", label, "value", label))
                .collect(Collectors.toList());

        return AiChatReplyVO.builder()
                .sessionId(sessionId)
                .replyType(AiReplyType.FOLLOW_UP.name())
                .intent(intent.name())
                .replyText(replyText)
                .parsedCondition(ParsedConditionMaps.toMap(condition))
                .missingFields(condition.getMissingFields())
                .followUpQuestion(condition.getFollowUpQuestion())
                .searchUrl(null)
                .flights(Collections.emptyList())
                .quickActions(quickActions)
                .build();
    }

    private AiChatReplyVO buildRecommendationReply(String sessionId, ParsedCondition condition,
                                                   FlightSearchResult result, DomainIntent intent) {
        List<Map<String, String>> quickActions = List.of(
                Map.of("label", "查看全部航班", "value", "查看全部航班"),
                Map.of("label", "换个时间", "value", "换个时间")
        );

        return AiChatReplyVO.builder()
                .sessionId(sessionId)
                .replyType(AiReplyType.FLIGHT_RECOMMENDATION.name())
                .intent(intent.name())
                .replyText(recommendationText(result))
                .parsedCondition(ParsedConditionMaps.toMap(condition))
                .missingFields(Collections.emptyList())
                .followUpQuestion(null)
                .searchUrl(result.searchUrl())
                .flights(result.flights())
                .quickActions(quickActions)
                .matchLevel(result.matchLevel().name())
                .appliedCondition(result.appliedCondition())
                .relaxedFields(result.relaxedFields())
                .fallbackReason(result.fallbackReason())
                .build();
    }

    private String recommendationText(FlightSearchResult result) {
        return switch (result.matchLevel()) {
            case EXACT -> "为您找到 " + result.flights().size() + " 个符合当前条件的航班：";
            case RELAXED -> "没有找到符合全部筛选条件的航班，已放宽次要条件，为您找到 "
                    + result.flights().size() + " 个航班：";
            case PARTIAL -> "以下是最近未来日期的相关航班：";
            case FALLBACK -> "没有找到符合条件的航班，为你推荐一些可能感兴趣的其他航班。";
        };
    }

    private AiChatReplyVO buildNoResultReply(String sessionId, ParsedCondition condition, DomainIntent intent) {
        List<Map<String, String>> quickActions = List.of(
                Map.of("label", "换个日期", "value", "换个日期"),
                Map.of("label", "查看全部航班", "value", "查看全部航班")
        );

        return AiChatReplyVO.builder()
                .sessionId(sessionId)
                .replyType(AiReplyType.NO_RESULT.name())
                .intent(intent.name())
                .replyText("抱歉，没有找到符合条件的航班。请尝试调整搜索条件。")
                .parsedCondition(ParsedConditionMaps.toMap(condition))
                .missingFields(Collections.emptyList())
                .followUpQuestion("是否需要调整搜索条件？")
                .searchUrl(null)
                .flights(Collections.emptyList())
                .quickActions(quickActions)
                .build();
    }

    private AiChatReplyVO buildTravelReply(String sessionId, DomainIntent intent, String replyText,
                                           List<Map<String, String>> quickActions) {
        return buildConversationalReply(sessionId, intent, replyText, quickActions);
    }

    private AiChatReplyVO buildConversationalReply(String sessionId, DomainIntent intent, String replyText,
                                                   List<Map<String, String>> quickActions) {
        AiReplyType replyType = switch (intent) {
            case TRAVEL_CHAT -> AiReplyType.TRAVEL_CHAT;
            case BOOKING_HELP -> AiReplyType.BOOKING_HELP;
            case OUT_OF_SCOPE -> AiReplyType.OUT_OF_SCOPE;
            case FLIGHT_QUERY, FLIGHT_QUERY_CONTINUATION -> AiReplyType.FOLLOW_UP;
        };

        return AiChatReplyVO.builder()
                .sessionId(sessionId)
                .replyType(replyType.name())
                .intent(intent.name())
                .replyText(replyText)
                .parsedCondition(Collections.emptyMap())
                .missingFields(Collections.emptyList())
                .followUpQuestion(null)
                .searchUrl(null)
                .flights(Collections.emptyList())
                .quickActions(quickActions)
                .build();
    }

    private Map<String, Object> buildExtraJson(AiChatReplyVO reply, ConversationState state) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("replyType", reply.getReplyType());
        extra.put("intent", reply.getIntent());
        extra.put("parsedCondition", reply.getParsedCondition() == null
                ? Collections.emptyMap() : reply.getParsedCondition());
        extra.put("missingFields", reply.getMissingFields() == null
                ? Collections.emptyList() : reply.getMissingFields());
        extra.put("followUpQuestion", reply.getFollowUpQuestion());
        extra.put("searchUrl", reply.getSearchUrl());
        extra.put("flights", reply.getFlights() == null ? Collections.emptyList() : reply.getFlights());
        extra.put("quickActions", reply.getQuickActions() == null
                ? Collections.emptyList() : reply.getQuickActions());
        extra.put("matchLevel", reply.getMatchLevel());
        extra.put("appliedCondition", reply.getAppliedCondition() == null
                ? Collections.emptyMap() : reply.getAppliedCondition());
        extra.put("relaxedFields", reply.getRelaxedFields() == null
                ? Collections.emptyList() : reply.getRelaxedFields());
        extra.put("fallbackReason", reply.getFallbackReason());
        Map<String, Object> travelContext = conversationStateService.travelContextMap(state);
        if (!travelContext.isEmpty()) {
            extra.put("travelContext", travelContext);
        }
        extra.put("conversationState", conversationStateService.toMap(state));
        return extra;
    }

    private List<Map<String, String>> travelQuickActions() {
        return List.of(
                Map.of("label", "帮我查机票", "value", "帮我查机票"),
                Map.of("label", "推荐旅行目的地", "value", "有哪些地方推荐去玩")
        );
    }

    private List<Map<String, String>> bookingHelpQuickActions() {
        return List.of(
                Map.of("label", "查询订单", "value", "订单怎么查看"),
                Map.of("label", "查机票", "value", "帮我查机票")
        );
    }

    private List<Map<String, String>> outOfScopeQuickActions() {
        return List.of(
                Map.of("label", "查机票", "value", "帮我查机票"),
                Map.of("label", "旅行建议", "value", "有哪些地方推荐去玩")
        );
    }

    private String outOfScopeReply() {
        return "抱歉，这个问题超出了 SkyBooker 航班与旅行助手的范围。我可以继续帮您查询机票、规划旅行方向，或说明订票、退票、改签、候补、订单等平台流程。";
    }

    private record SearchReply(AiChatReplyVO reply, RecommendationLog recommendationLog) {
    }
}
