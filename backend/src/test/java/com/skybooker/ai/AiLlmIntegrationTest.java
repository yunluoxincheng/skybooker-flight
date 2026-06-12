package com.skybooker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.dto.AiChatRequest;
import com.skybooker.ai.parser.LlmChatClient;
import com.skybooker.common.AbstractIntegrationTest;
import com.skybooker.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "ai.llm.enabled=true",
        "ai.llm.base-url=https://llm.example.test/v1",
        "ai.llm.api-key=test-key",
        "ai.llm.model=test-model"
})
class AiLlmIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private LlmChatClient llmChatClient;

    private String tomorrowStr;

    @BeforeEach
    void setUp() {
        tomorrowStr = LocalDate.now().plusDays(1).toString();
        jdbcTemplate.update(
                "UPDATE flight SET remaining_seats = 29, departure_time = TIMESTAMP(?, TIME(departure_time)), " +
                        "arrival_time = TIMESTAMP(?, TIME(arrival_time)) WHERE id BETWEEN 2 AND 5",
                tomorrowStr, tomorrowStr);
        reset(llmChatClient);
    }

    @Test
    void chat_llmSuccess_returnsDatabaseBackedRecommendationAndIgnoresFabricatedFields() throws Exception {
        when(llmChatClient.complete(anyString(), anyString())).thenReturn("""
                {
                  "departureCity": "上海",
                  "arrivalCity": "北京",
                  "departureDate": "%s",
                  "passengerCount": 1,
                  "cabinClass": "ECONOMY",
                  "airlineRaw": "南方航空",
                  "sort": "PRICE_ASC",
                  "flightNo": "FAKE-001",
                  "price": 1,
                  "remainingSeats": 9999,
                  "detailUrl": "/fake-detail",
                  "bookingUrl": "/fake-booking"
                }
                """.formatted(tomorrowStr));

        AiChatRequest request = new AiChatRequest();
        request.setMessage("帮我订一张上海到北京的票");

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr))
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"))
                .andExpect(jsonPath("$.data.flights").isArray())
                .andReturn();

        ApiResponse<Map> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) response.getData();
        List<Map<String, Object>> flights = (List<Map<String, Object>>) data.get("flights");
        assertThat(flights).isNotEmpty();
        assertThat(flights.get(0).get("flightNo")).isNotEqualTo("FAKE-001");
        assertThat(flights.get(0).get("detailUrl")).asString().doesNotContain("fake");
        assertThat(flights.get(0).get("bookingUrl")).asString().doesNotContain("fake");
    }

    @Test
    void chat_llmFailure_fallsBackToRuleParser() throws Exception {
        when(llmChatClient.complete(anyString(), anyString()))
                .thenThrow(new com.skybooker.ai.parser.LlmIntentParseException("timeout"));

        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + "便宜");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.sort").value("PRICE_ASC"));
    }

    @Test
    void chat_llmMultiTurnContextMerge_doesNotCallLlmForInternalBlankFollowUp() throws Exception {
        when(llmChatClient.complete(anyString(), anyString()))
                .thenReturn("""
                        {
                          "departureCity": "上海",
                          "arrivalCity": "北京",
                          "missingFields": ["departureDate"],
                          "followUpQuestion": "请问哪天出发？"
                        }
                        """)
                .thenReturn("""
                        {
                          "departureDate": "%s"
                        }
                        """.formatted(tomorrowStr));

        AiChatRequest first = new AiChatRequest();
        first.setMessage("我要从上海到北京");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FOLLOW_UP"))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest second = new AiChatRequest();
        second.setSessionId(sessionId);
        second.setMessage("明天出发");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr));

        verify(llmChatClient, times(2)).complete(anyString(), anyString());
    }

    @Test
    void chat_llmRecommendationPersistence_usesNormalizedParsedCondition() throws Exception {
        jdbcTemplate.update("UPDATE flight SET remaining_seats = 0 WHERE id = 2");

        when(llmChatClient.complete(anyString(), anyString())).thenReturn("""
                {
                  "departureCity": "上海",
                  "arrivalCity": "北京",
                  "departureDate": "%s",
                  "flightNo": "FAKE-002",
                  "price": 2
                }
                """.formatted(tomorrowStr));

        AiChatRequest request = new AiChatRequest();
        request.setMessage("上海北京机票");

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.passengerCount").value(1))
                .andReturn();

        ApiResponse<Map> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) response.getData();

        assertThat(data.get("replyType")).isEqualTo("FLIGHT_RECOMMENDATION");
        List<Map<String, Object>> flights = (List<Map<String, Object>>) data.get("flights");
        assertThat(flights).isNotEmpty();
        assertThat(flights)
                .extracting(flight -> flight.get("flightNo"))
                .doesNotContain("CZ3101");
        assertThat(flights)
                .extracting(flight -> ((Number) flight.get("remainingSeats")).intValue())
                .allMatch(remainingSeats -> remainingSeats > 0);

        String sessionId = (String) data.get("sessionId");
        String parsedJson = jdbcTemplate.queryForObject(
                "SELECT r.parsed_condition_json FROM ai_recommendation_record r " +
                        "JOIN ai_chat_session s ON r.session_id = s.id " +
                        "WHERE s.public_session_id = ? LIMIT 1",
                String.class, sessionId);
        var parsedNode = objectMapper.readTree(parsedJson);
        assertThat(parsedNode.path("departureCity").asText()).isEqualTo("上海");
        assertThat(parsedNode.path("arrivalCity").asText()).isEqualTo("北京");
        assertThat(parsedNode.path("departureDate").asText()).isEqualTo(tomorrowStr);
        assertThat(parsedNode.path("passengerCount").asInt()).isEqualTo(1);
        assertThat(parsedJson).doesNotContain("FAKE-002");
        assertThat(parsedNode.has("price")).isFalse();
    }
}
