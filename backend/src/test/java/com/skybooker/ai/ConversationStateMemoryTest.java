package com.skybooker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.enums.AiReplyType;
import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.state.ConversationState;
import com.skybooker.ai.state.ConversationStateService;
import com.skybooker.ai.vo.AiChatReplyVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationStateMemoryTest {

    @Test
    void keepsActiveConditionAfterSuccessfulSearchAndTracksExecutedDefaults() {
        ConversationStateService service = new ConversationStateService(new ObjectMapper());
        ParsedCondition active = ParsedCondition.builder()
                .departureCity("广州").arrivalCity("北京").build();
        AiChatReplyVO reply = AiChatReplyVO.builder()
                .intent("FLIGHT_QUERY").replyType(AiReplyType.FLIGHT_RECOMMENDATION.name())
                .appliedCondition(java.util.Map.of("departureCity", "广州", "arrivalCity", "北京",
                        "departureDate", "2026-07-13"))
                .missingFields(List.of()).build();

        ConversationState state = service.nextState(ConversationState.empty(), reply, active,
                null, null, null);

        assertThat(state.getActiveFlightCondition().getDepartureCity()).isEqualTo("广州");
        assertThat(state.getActiveFlightCondition().getDepartureDate()).isNull();
        assertThat(state.getLastExecutedFlightCondition().getDepartureDate())
                .isEqualTo(LocalDate.of(2026, 7, 13));
        assertThat(service.toMap(state)).containsKeys("activeFlightCondition", "lastExecutedFlightCondition");
    }
}
