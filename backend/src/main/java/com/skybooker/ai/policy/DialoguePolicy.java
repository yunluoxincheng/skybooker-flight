package com.skybooker.ai.policy;

import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.state.ConversationState;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DialoguePolicy {

    private static final List<String> RECOMMENDATION_KEYWORDS = List.of(
            "推荐", "去哪", "去哪里", "目的地", "地方", "看海"
    );
    private static final List<String> FLIGHT_KEYWORDS = List.of(
            "机票", "航班", "查票", "看看票", "有票", "票价", "余票"
    );

    public DialogueAction decide(String message, DomainIntent intent,
                                 ParsedCondition condition, ConversationState state) {
        return switch (intent) {
            case OUT_OF_SCOPE -> new DialogueAction(DialogueActionType.OUT_OF_SCOPE);
            case BOOKING_HELP -> new DialogueAction(DialogueActionType.BOOKING_HELP);
            case TRAVEL_CHAT -> destinationRecommendationRequested(message)
                    ? new DialogueAction(DialogueActionType.RECOMMEND_DESTINATION)
                    : new DialogueAction(DialogueActionType.TRAVEL_PLAN);
            case FLIGHT_QUERY, FLIGHT_QUERY_CONTINUATION -> decideFlightAction(message, condition, state);
        };
    }

    private DialogueAction decideFlightAction(String message, ParsedCondition condition, ConversationState state) {
        if (mixedRecommendationAndFlightRequested(message)
                && (condition == null || condition.getArrivalCity() == null)) {
            return new DialogueAction(DialogueActionType.RECOMMEND_DESTINATION_AND_FOLLOW_UP);
        }
        if (condition == null || !condition.isComplete()) {
            return new DialogueAction(DialogueActionType.FOLLOW_UP);
        }
        return new DialogueAction(DialogueActionType.SEARCH_FLIGHTS);
    }

    private boolean destinationRecommendationRequested(String message) {
        String text = message == null ? "" : message;
        return containsAny(text, RECOMMENDATION_KEYWORDS)
                || (text.contains("周末") && text.contains("玩"))
                || (text.contains("三天") && text.contains("玩"));
    }

    private boolean mixedRecommendationAndFlightRequested(String message) {
        String text = message == null ? "" : message;
        return containsAny(text, RECOMMENDATION_KEYWORDS) && containsAny(text, FLIGHT_KEYWORDS);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
