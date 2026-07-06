package com.skybooker.ai.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.entity.AiChatMessage;
import com.skybooker.ai.enums.AiReplyType;
import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.parser.ParsedConditionMaps;
import com.skybooker.ai.tool.DestinationRecommendation;
import com.skybooker.ai.tool.TravelPlanResult;
import com.skybooker.ai.vo.AiChatReplyVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationStateService {

    private static final String CONVERSATION_STATE = "conversationState";
    private static final String TRAVEL_CONTEXT = "travelContext";
    private static final String DESTINATION_CITY = "destinationCity";

    private final ObjectMapper objectMapper;

    public ConversationState load(List<AiChatMessage> messages) {
        ConversationState state = ConversationState.empty();
        for (AiChatMessage message : messages) {
            if (!"ASSISTANT".equals(message.getRole())
                    || message.getExtraJson() == null
                    || message.getExtraJson().isBlank()) {
                continue;
            }
            try {
                Map<String, Object> extra = objectMapper.readValue(
                        message.getExtraJson(), new TypeReference<LinkedHashMap<String, Object>>() {});
                Object conversationState = extra.get(CONVERSATION_STATE);
                if (conversationState instanceof Map<?, ?>) {
                    state = fromMap(conversationState);
                } else {
                    state = applyLegacyExtra(state, extra);
                }
            } catch (Exception e) {
                log.warn("Failed to load AI conversation state from message {}", message.getId(), e);
            }
        }
        return state;
    }

    public ConversationState nextState(ConversationState previous, AiChatReplyVO reply,
                                       ParsedCondition condition,
                                       DestinationRecommendation destinationRecommendation,
                                       TravelPlanResult travelPlanResult,
                                       String explicitDestinationCity) {
        ConversationState.ConversationStateBuilder builder = previous == null
                ? ConversationState.empty().toBuilder()
                : previous.toBuilder();
        builder.lastIntent(reply.getIntent());
        builder.lastReplyType(reply.getReplyType());

        if (AiReplyType.FOLLOW_UP.name().equals(reply.getReplyType())) {
            builder.pendingFlightCondition(condition);
            builder.pendingMissingFields(reply.getMissingFields() == null
                    ? List.of() : new ArrayList<>(reply.getMissingFields()));
        } else {
            builder.pendingFlightCondition(null);
            builder.pendingMissingFields(new ArrayList<>());
            if (condition != null && reply.getIntent() != null && reply.getIntent().startsWith("FLIGHT_QUERY")) {
                builder.lastFlightCondition(condition);
            }
        }

        if (destinationRecommendation != null && destinationRecommendation.primaryCity() != null) {
            builder.recommendedDestinationCity(destinationRecommendation.primaryCity());
            builder.recommendedDestinationCandidates(new ArrayList<>(destinationRecommendation.candidateCities()));
        } else if (explicitDestinationCity != null) {
            builder.recommendedDestinationCity(explicitDestinationCity);
            builder.recommendedDestinationCandidates(List.of(explicitDestinationCity));
        } else if (travelPlanResult != null && travelPlanResult.destinationCity() != null) {
            builder.recommendedDestinationCity(travelPlanResult.destinationCity());
        }

        return builder.build();
    }

    public Map<String, Object> toMap(ConversationState state) {
        if (state == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (state.getPendingFlightCondition() != null) {
            map.put("pendingFlightCondition", ParsedConditionMaps.toMap(state.getPendingFlightCondition()));
        }
        map.put("pendingMissingFields", state.getPendingMissingFields() == null
                ? List.of() : state.getPendingMissingFields());
        if (state.getLastFlightCondition() != null) {
            map.put("lastFlightCondition", ParsedConditionMaps.toMap(state.getLastFlightCondition()));
        }
        if (state.getRecommendedDestinationCity() != null) {
            map.put("recommendedDestinationCity", state.getRecommendedDestinationCity());
        }
        map.put("recommendedDestinationCandidates", state.getRecommendedDestinationCandidates() == null
                ? List.of() : state.getRecommendedDestinationCandidates());
        if (state.getLastIntent() != null) {
            map.put("lastIntent", state.getLastIntent());
        }
        if (state.getLastReplyType() != null) {
            map.put("lastReplyType", state.getLastReplyType());
        }
        return map;
    }

    public Map<String, Object> travelContextMap(ConversationState state) {
        if (state == null || state.getRecommendedDestinationCity() == null) {
            return Collections.emptyMap();
        }
        return Map.of(DESTINATION_CITY, state.getRecommendedDestinationCity());
    }

    private ConversationState fromMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return ConversationState.empty();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null) {
                map.put(key.toString(), item);
            }
        });
        return ConversationState.builder()
                .pendingFlightCondition(ParsedConditionMaps.fromObject(map.get("pendingFlightCondition")))
                .pendingMissingFields(stringList(map.get("pendingMissingFields")))
                .lastFlightCondition(ParsedConditionMaps.fromObject(map.get("lastFlightCondition")))
                .recommendedDestinationCity(string(map.get("recommendedDestinationCity")))
                .recommendedDestinationCandidates(stringList(map.get("recommendedDestinationCandidates")))
                .lastIntent(string(map.get("lastIntent")))
                .lastReplyType(string(map.get("lastReplyType")))
                .build();
    }

    private ConversationState applyLegacyExtra(ConversationState state, Map<String, Object> extra) {
        ConversationState.ConversationStateBuilder builder = state.toBuilder();
        String replyType = string(extra.get("replyType"));
        String intent = string(extra.get("intent"));
        ParsedCondition parsedCondition = ParsedConditionMaps.fromObject(extra.get("parsedCondition"));
        List<String> missingFields = stringList(extra.get("missingFields"));

        builder.lastReplyType(replyType);
        builder.lastIntent(intent);

        if (AiReplyType.FOLLOW_UP.name().equals(replyType) && !missingFields.isEmpty()) {
            builder.pendingFlightCondition(parsedCondition);
            builder.pendingMissingFields(missingFields);
        } else if (intent != null && intent.startsWith("FLIGHT_QUERY")) {
            builder.pendingFlightCondition(null);
            builder.pendingMissingFields(new ArrayList<>());
            builder.lastFlightCondition(parsedCondition);
        }

        Object travelContext = extra.get(TRAVEL_CONTEXT);
        if (travelContext instanceof Map<?, ?> context) {
            Object destinationCity = context.get(DESTINATION_CITY);
            if (destinationCity instanceof String city && !city.isBlank()) {
                builder.recommendedDestinationCity(city);
            }
        }
        return builder.build();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = string(item);
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    private String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
