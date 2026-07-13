package com.skybooker.ai.state;

import com.skybooker.ai.parser.ParsedCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ConversationState {

    private ParsedCondition pendingFlightCondition;
    @Builder.Default
    private List<String> pendingMissingFields = new ArrayList<>();
    private ParsedCondition lastFlightCondition;
    private ParsedCondition activeFlightCondition;
    private ParsedCondition lastExecutedFlightCondition;
    private String recommendedDestinationCity;
    @Builder.Default
    private List<String> recommendedDestinationCandidates = new ArrayList<>();
    private String lastIntent;
    private String lastReplyType;

    public static ConversationState empty() {
        return ConversationState.builder()
                .pendingMissingFields(new ArrayList<>())
                .recommendedDestinationCandidates(new ArrayList<>())
                .build();
    }

    public boolean hasPendingFlightQuery() {
        return pendingMissingFields != null && !pendingMissingFields.isEmpty();
    }

    public boolean hasActiveFlightQuery() {
        return activeFlightCondition != null;
    }
}
