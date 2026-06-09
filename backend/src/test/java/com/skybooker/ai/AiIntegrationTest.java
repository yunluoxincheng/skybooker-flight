package com.skybooker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.dto.AiChatRequest;
import com.skybooker.common.AbstractIntegrationTest;
import com.skybooker.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String adminToken;
    private String tomorrowStr;

    @BeforeEach
    void setUp() throws Exception {
        userToken = obtainUserToken();
        adminToken = obtainAdminToken();

        // Refresh seed flight dates (IDs 2-5) to tomorrow, skip id=1 to avoid polluting order tests
        String tomorrow = LocalDate.now().plusDays(1).toString();
        tomorrowStr = tomorrow;
        jdbcTemplate.update(
                "UPDATE flight SET departure_time = TIMESTAMP(?, TIME(departure_time)), " +
                        "arrival_time = TIMESTAMP(?, TIME(arrival_time)) WHERE id BETWEEN 2 AND 5",
                tomorrow, tomorrow);
    }

    // --- 6.1 Chat session tests ---

    @Test
    void chat_anonymousCreatesSession() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("我想从上海飞北京明天");

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.replyType").isNotEmpty())
                .andReturn();

        ApiResponse<Map> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("sessionId")).isNotNull();
    }

    @Test
    void chat_userOwnedSession() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("我想从上海飞北京");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty());
    }

    @Test
    void chat_existingSessionResume() throws Exception {
        AiChatRequest firstRequest = new AiChatRequest();
        firstRequest.setMessage("我想从上海飞北京");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest secondRequest = new AiChatRequest();
        secondRequest.setSessionId(sessionId);
        secondRequest.setMessage("明天出发");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId));
    }

    @Test
    void chat_emptyMessageRejected() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // --- 6.2 Parser / response tests ---

    @Test
    void chat_completeRouteReturnsRecommendationOrNoResult() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + "出发，便宜的");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data.replyType").value(either(
                        org.hamcrest.Matchers.is("FLIGHT_RECOMMENDATION"))
                        .or(org.hamcrest.Matchers.is("NO_RESULT"))))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.replyText").isNotEmpty())
                .andExpect(jsonPath("$.data.parsedCondition").isNotEmpty())
                .andExpect(jsonPath("$.data.missingFields").isArray());
    }

    @Test
    void chat_missingFieldsReturnsFollowUp() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("帮我买机票");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data.missingFields").isArray())
                .andExpect(jsonPath("$.data.missingFields").isNotEmpty())
                .andExpect(jsonPath("$.data.followUpQuestion").isNotEmpty())
                .andExpect(jsonPath("$.data.flights").isArray())
                .andExpect(jsonPath("$.data.searchUrl").isEmpty());
    }

    @Test
    void chat_followUpStableResponseShape() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("随便说说");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.replyType").exists())
                .andExpect(jsonPath("$.data.replyText").isNotEmpty())
                .andExpect(jsonPath("$.data.parsedCondition").exists())
                .andExpect(jsonPath("$.data.missingFields").isArray())
                .andExpect(jsonPath("$.data.flights").isArray());
    }

    @Test
    void chat_relativeDateTomorrow() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京明天出发");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").exists());
    }

    @Test
    void chat_routeParsing_noTrailingDelimiter() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京明天出发");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"));
    }

    @Test
    void chat_responseSchema_stableFields() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("随便聊聊");

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.replyType").isNotEmpty())
                .andExpect(jsonPath("$.data.replyText").isNotEmpty())
                .andExpect(jsonPath("$.data.parsedCondition").isNotEmpty())
                .andExpect(jsonPath("$.data.missingFields").isArray())
                .andExpect(jsonPath("$.data.flights").isArray())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json).get("data");
        assertThat(root.has("sessionId")).isTrue();
        assertThat(root.has("replyType")).isTrue();
        assertThat(root.has("replyText")).isTrue();
        assertThat(root.has("parsedCondition")).isTrue();
        assertThat(root.has("missingFields")).isTrue();
        assertThat(root.has("followUpQuestion")).isTrue();
        assertThat(root.has("searchUrl")).isTrue();
        assertThat(root.has("flights")).isTrue();
        assertThat(root.has("quickActions")).isTrue();
    }

    @Test
    void chat_pricePreferenceCheap() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + "便宜");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.sort").value("PRICE_ASC"));
    }

    @Test
    void chat_directFlightPreference() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + "直飞");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.directOnly").value(true));
    }

    @Test
    void chat_airlineFullName() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + "南方航空");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.airlineRaw").value("南方航空"));
    }

    @Test
    void chat_airlineCode() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + " MU航班");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.airlineRaw").value("东方航空"));
    }

    @Test
    void chat_passengerCount() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + " 3人");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.passengerCount").value(3));
    }

    @Test
    void chat_cabinClass() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + "经济舱");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"));
    }

    @Test
    void chat_noResultSuccessResponse() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从三亚到拉萨" + tomorrowStr + "出发");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data.replyType").value("NO_RESULT"))
                .andExpect(jsonPath("$.data.flights").isArray())
                .andExpect(jsonPath("$.data.flights").isEmpty())
                .andExpect(jsonPath("$.data.parsedCondition").isNotEmpty());
    }

    // --- 6.3 Recommendation data tests ---

    @Test
    void chat_recommendationReturnsFlightCardsWithData() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr);

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) response.getData();

        if ("FLIGHT_RECOMMENDATION".equals(data.get("replyType"))) {
            java.util.List<Map<String, Object>> flights =
                    (java.util.List<Map<String, Object>>) data.get("flights");
            assertThat(flights).isNotEmpty();
            assertThat(flights.size()).isLessThanOrEqualTo(3);
            Map<String, Object> firstFlight = flights.get(0);
            assertThat(firstFlight).containsKey("flightId");
            assertThat(firstFlight).containsKey("flightNo");
            assertThat(firstFlight).containsKey("airlineName");
            assertThat(firstFlight).containsKey("departureCity");
            assertThat(firstFlight).containsKey("arrivalCity");
            assertThat(firstFlight).containsKey("price");
            assertThat(firstFlight).containsKey("detailUrl");
        }
    }

    // --- 6.4 Persistence tests ---

    @Test
    void chat_messagesPersisted() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("我想从上海飞北京");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.data.messages[0].content").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[0].messageType").value("TEXT"))
                .andExpect(jsonPath("$.data.messages[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.messages[1].extra").isNotEmpty());
    }

    @Test
    void chat_recommendationRecordPersistedWithOriginalQuery() throws Exception {
        String originalQuery = "从上海到北京" + tomorrowStr;
        AiChatRequest request = new AiChatRequest();
        request.setMessage(originalQuery);

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) chatResponse.getData();

        if ("FLIGHT_RECOMMENDATION".equals(data.get("replyType"))) {
            String sessionId = (String) data.get("sessionId");
            String queryText = jdbcTemplate.queryForObject(
                    "SELECT r.query_text FROM ai_recommendation_record r " +
                            "JOIN ai_chat_session s ON r.session_id = s.id " +
                            "WHERE s.public_session_id = ? LIMIT 1",
                    String.class, sessionId);
            assertThat(queryText).isEqualTo(originalQuery);
        }
    }

    // --- 6.5 Session ownership tests ---

    @Test
    void chat_userCanAccessOwnSession() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("帮我查机票");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void chat_crossUserAccessRejected() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("帮我查机票");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        // Second user - use a different login
        String user2Token = obtainSecondUserToken();
        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void chat_anonymousCanAccessAnonymousSession() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("查机票");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages"))
                .andExpect(status().isOk());
    }

    @Test
    void chat_anonymousCannotAccessOwnedSession() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("查机票");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chat_userCannotAccessAnonymousSession() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("查机票");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void chat_missingSessionRejected() throws Exception {
        mockMvc.perform(get("/api/ai/sessions/nonexistent-session-id/messages"))
                .andExpect(status().isNotFound());
    }

    @Test
    void chat_deletedSessionCannotResume() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("查机票");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        mockMvc.perform(delete("/api/ai/sessions/" + sessionId))
                .andExpect(status().isOk());

        AiChatRequest resumeRequest = new AiChatRequest();
        resumeRequest.setSessionId(sessionId);
        resumeRequest.setMessage("继续查");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resumeRequest)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void chat_deleteSession() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("查机票");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        mockMvc.perform(delete("/api/ai/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Verify session is marked deleted
        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages"))
                .andExpect(status().isNotFound());
    }

    // --- PR review fix tests ---

    @Test
    void chat_deletedSessionReturns404NotStateError() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("查机票");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        mockMvc.perform(delete("/api/ai/sessions/" + sessionId))
                .andExpect(status().isOk());

        AiChatRequest resumeRequest = new AiChatRequest();
        resumeRequest.setSessionId(sessionId);
        resumeRequest.setMessage("继续查");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resumeRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void chat_multiTurnMergesDate() throws Exception {
        AiChatRequest firstRequest = new AiChatRequest();
        firstRequest.setMessage("从上海到北京");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FOLLOW_UP"))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest secondRequest = new AiChatRequest();
        secondRequest.setSessionId(sessionId);
        secondRequest.setMessage("明天出发");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr));
    }

    @Test
    void chat_dayAfterTomorrow() throws Exception {
        AiChatRequest request = new AiChatRequest();
        String expectedDate = LocalDate.now().plusDays(3).toString();
        request.setMessage("从上海到北京大后天出发");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(expectedDate));
    }

    @Test
    void chat_searchUrlProperlyEncoded() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + "便宜");

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) response.getData();

        if ("FLIGHT_RECOMMENDATION".equals(data.get("replyType"))) {
            String searchUrl = (String) data.get("searchUrl");
            assertThat(searchUrl).isNotNull();
            assertThat(searchUrl).contains("departureCity=");
            assertThat(searchUrl).doesNotContain("departureCity= ").startsWith("/flights");
        }
    }

    // --- Session message history shape ---

    @Test
    void sessionMessages_stableResponseShape() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("查机票");

        MvcResult chatResult = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> chatResponse = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) chatResponse.getData()).get("sessionId");

        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.messages[0].role").exists())
                .andExpect(jsonPath("$.data.messages[0].content").exists())
                .andExpect(jsonPath("$.data.messages[0].messageType").exists())
                .andExpect(jsonPath("$.data.messages[0].createdAt").exists());
    }

    @SuppressWarnings("unchecked")
    private String obtainUserToken() throws Exception {
        com.skybooker.auth.dto.UserLoginDTO dto = new com.skybooker.auth.dto.UserLoginDTO();
        dto.setEmail("user1@example.com");
        dto.setPassword("User@123456");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map<String, Object>> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        return (String) ((Map<String, Object>) response.getData()).get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private String obtainAdminToken() throws Exception {
        com.skybooker.admin.dto.AdminLoginDTO dto = new com.skybooker.admin.dto.AdminLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("Admin@123456");

        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map<String, Object>> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        return (String) ((Map<String, Object>) response.getData()).get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private String obtainSecondUserToken() throws Exception {
        // Register a second user for cross-user testing
        jdbcTemplate.update(
                "INSERT IGNORE INTO users(email, password_hash, nickname, real_name, role, email_verified) " +
                        "VALUES('user2@test.com', '$2b$12$mxGK3588bVIlwCYgjrqa1.1esZ8vbAKALvroPmpAvJfGt3VO781oy', '测试用户2', '测试', 'USER', 1)");

        com.skybooker.auth.dto.UserLoginDTO dto = new com.skybooker.auth.dto.UserLoginDTO();
        dto.setEmail("user2@test.com");
        dto.setPassword("User@123456");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map<String, Object>> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        return (String) ((Map<String, Object>) response.getData()).get("accessToken");
    }
}
