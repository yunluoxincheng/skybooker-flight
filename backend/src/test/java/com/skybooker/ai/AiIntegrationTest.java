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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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
    void chat_outOfScopeStableResponseShape() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("帮我写代码");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.replyType").value("OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.data.intent").value("OUT_OF_SCOPE"))
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
        request.setMessage("你好");

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.replyType").isNotEmpty())
                .andExpect(jsonPath("$.data.intent").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.replyText").isNotEmpty())
                .andExpect(jsonPath("$.data.parsedCondition").exists())
                .andExpect(jsonPath("$.data.missingFields").isArray())
                .andExpect(jsonPath("$.data.flights").isArray())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json).get("data");
        assertThat(root.has("sessionId")).isTrue();
        assertThat(root.has("replyType")).isTrue();
        assertThat(root.has("intent")).isTrue();
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
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.matchLevel").value("FALLBACK"))
                .andExpect(jsonPath("$.data.flights").isNotEmpty())
                .andExpect(jsonPath("$.data.parsedCondition").isNotEmpty());
    }

    @Test
    void chat_greetingReturnsConversationalReplyWithoutMissingSearchFields() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("你好");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.intent").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.missingFields").isArray())
                .andExpect(jsonPath("$.data.missingFields").isEmpty())
                .andExpect(jsonPath("$.data.flights").isArray())
                .andExpect(jsonPath("$.data.flights").isEmpty())
                .andExpect(jsonPath("$.data.searchUrl").isEmpty());
    }

    @Test
    void chat_travelAdviceDoesNotFabricateConcreteFlightFacts() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("北京有什么好玩");

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.intent").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.flights").isEmpty())
                .andExpect(jsonPath("$.data.searchUrl").isEmpty())
                .andReturn();

        ApiResponse<Map> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        String replyText = (String) ((Map<String, Object>) response.getData()).get("replyText");
        assertThat(replyText).doesNotContain("/booking/");
        assertThat(replyText).doesNotContain("CZ3101");
        assertThat(replyText).doesNotContain("余票");
    }

    @Test
    void chat_platformHelpUsesFixedGuidance() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("退票怎么操作");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("BOOKING_HELP"))
                .andExpect(jsonPath("$.data.intent").value("BOOKING_HELP"))
                .andExpect(jsonPath("$.data.replyText").value(containsString("订单详情")))
                .andExpect(jsonPath("$.data.flights").isEmpty());
    }

    @Test
    void chat_outOfScopeIsRefused() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("帮我写代码");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.data.intent").value("OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.data.replyText").value(containsString("超出")));
    }

    @Test
    void chat_destinationPhraseBoundaries() throws Exception {
        // 裸目的地“我想去北京”不再直接查库，走 TRAVEL_CHAT 澄清（无 parsedCondition/missingFields）
        AiChatRequest destinationOnly = new AiChatRequest();
        destinationOnly.setMessage("我想去北京");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(destinationOnly)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.intent").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.missingFields").isEmpty());

        AiChatRequest travelAdvice = new AiChatRequest();
        travelAdvice.setMessage("北京有什么好玩");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(travelAdvice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.intent").value("TRAVEL_CHAT"));
    }

    @Test
    void chat_travelContextSuppliesDestinationForLaterFlightQuery() throws Exception {
        AiChatRequest first = new AiChatRequest();
        first.setMessage("你好，我想去上海迪士尼玩");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.intent").value("TRAVEL_CHAT"))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest second = new AiChatRequest();
        second.setSessionId(sessionId);
        second.setMessage("我现在在广州，准备八月初去，大概2到5号这几天，帮我看看这几天的机票");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").doesNotExist())
                .andExpect(jsonPath("$.data.parsedCondition.departureDateStart").value("2026-08-02"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDateEnd").value("2026-08-05"))
                .andExpect(jsonPath("$.data.missingFields").isEmpty());
    }

    @Test
    void chat_travelContextSurvivesIntermediateTravelChat() throws Exception {
        AiChatRequest first = new AiChatRequest();
        first.setMessage("你好，我想去上海迪士尼玩");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest second = new AiChatRequest();
        second.setSessionId(sessionId);
        second.setMessage("那边适合玩几天？");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.intent").value("TRAVEL_CHAT"));

        AiChatRequest third = new AiChatRequest();
        third.setSessionId(sessionId);
        third.setMessage("我现在在广州，8月2到5号这几天机票");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(third)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDateStart").value("2026-08-02"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDateEnd").value("2026-08-05"))
                .andExpect(jsonPath("$.data.missingFields").isEmpty());
    }

    @Test
    void chat_multiTurnKeepsOptionalPassengerAndCabinFilters() throws Exception {
        // 第一轮：路线+人数+舱位但缺日期 → 使用业务日期直接搜索，并保留可选筛选条件
        AiChatRequest first = new AiChatRequest();
        first.setMessage("查上海到北京两个人经济舱");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.parsedCondition.passengerCount").value(2))
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        // 第二轮：只回复“明天”，人数和舱位应从上一轮继承，不丢
        AiChatRequest second = new AiChatRequest();
        second.setSessionId(sessionId);
        second.setMessage("明天");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.passengerCount").value(2))
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr));
    }

    @Test
    void chat_multiTurnCompleteReplyStillKeepsPreviousOptionalCabinFilter() throws Exception {
        // 第一轮：路线+舱位但缺日期 → 使用业务日期直接搜索
        AiChatRequest first = new AiChatRequest();
        first.setMessage("查上海到北京经济舱");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        // 第二轮：用户补了完整路线+日期但没重复“经济舱”，仍应继承上一轮可选舱位筛选。
        AiChatRequest second = new AiChatRequest();
        second.setSessionId(sessionId);
        second.setMessage("上海到北京明天");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr));
    }

    @Test
    void chat_regularFlightQueryMergesPreviousRecommendationFilters() throws Exception {
        // 第一轮是完整独立查询，assistant 会返回推荐或无结果，但不是 FOLLOW_UP。
        AiChatRequest first = new AiChatRequest();
        first.setMessage("查上海到北京明天经济舱");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        // 没有明确重置语义时，即使路由为普通 FLIGHT_QUERY，也应继承未覆盖的会话条件。
        AiChatRequest second = new AiChatRequest();
        second.setSessionId(sessionId);
        second.setMessage("查广州到深圳明天");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("深圳"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr))
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"));
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
                .andExpect(jsonPath("$.data.quickActions[0].label").value("凌晨"))
                .andExpect(jsonPath("$.data.quickActions[1].label").value("上午"))
                .andExpect(jsonPath("$.data.quickActions[2].label").value("下午"))
                .andExpect(jsonPath("$.data.quickActions[3].label").value("晚间"))
                .andExpect(jsonPath("$.data.searchUrl").isNotEmpty())
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
            assertThat(firstFlight).containsKeys("id", "journeyType", "segments", "originCity",
                    "destinationCity", "totalDurationMinutes", "estimatedAmount", "availableSeats",
                    "sellable");
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
                .andExpect(jsonPath("$.data.messages[1].extra.replyType").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[1].extra.intent").isNotEmpty())
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
        firstRequest.setMessage("查上海到北京机票");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
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
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY_CONTINUATION"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr));
    }

    @Test
    void chat_multiTurnAcceptsNaturalDepartureContinuation() throws Exception {
        String sessionId = startRouteFollowUpSession("查上海到北京机票");

        AiChatRequest secondRequest = new AiChatRequest();
        secondRequest.setSessionId(sessionId);
        secondRequest.setMessage("我想明天上午从广州出发");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY_CONTINUATION"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeStart").value("06:00"))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeEnd").value("12:00"));
    }

    @Test
    void chat_multiTurnAcceptsNaturalRouteAndDateContinuation() throws Exception {
        String sessionId = startRouteFollowUpSession("查上海到北京机票");

        AiChatRequest secondRequest = new AiChatRequest();
        secondRequest.setSessionId(sessionId);
        secondRequest.setMessage("我从广州出发，目的地北京，明天上午");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY_CONTINUATION"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr));
    }

    @Test
    void chat_activeConditionAppliesIncrementalTimeDestinationAndDepartureUpdates() throws Exception {
        AiChatRequest first = new AiChatRequest();
        first.setMessage("广州到北京明天上午经济舱");
        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeStart").value("06:00"))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeEnd").value("12:00"))
                .andReturn();
        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest afternoon = new AiChatRequest();
        afternoon.setSessionId(sessionId);
        afternoon.setMessage("下午的呢");
        mockMvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(afternoon)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr))
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeStart").value("12:00"))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeEnd").value("18:00"));

        AiChatRequest destination = new AiChatRequest();
        destination.setSessionId(sessionId);
        destination.setMessage("改成上海");
        mockMvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(destination)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeStart").value("12:00"));

        AiChatRequest departure = new AiChatRequest();
        departure.setSessionId(sessionId);
        departure.setMessage("从深圳出发");
        mockMvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(departure)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("深圳"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr))
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"));
    }

    @Test
    void chat_timeUnlimitedOnlyClearsDepartureTimePeriod() throws Exception {
        AiChatRequest first = new AiChatRequest();
        first.setMessage("广州到北京明天上午");
        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeStart").value("06:00"))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeEnd").value("12:00"))
                .andReturn();
        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest clearTime = new AiChatRequest();
        clearTime.setSessionId(sessionId);
        clearTime.setMessage("时间不限");
        mockMvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clearTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeStart").doesNotExist())
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeEnd").doesNotExist());
    }

    @Test
    void chat_plainResetIsSafeAndClearsConditions() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("清空条件");

        mockMvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyText").value("已清空当前航班查询条件，我们可以重新开始。"))
                .andExpect(jsonPath("$.data.parsedCondition").isEmpty());
    }

    @Test
    void chat_resetWithNewConditionsExecutesTheNewSearch() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("重新查询明天广州到北京");

        mockMvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.replyText").value(
                        org.hamcrest.Matchers.not("已清空当前航班查询条件，我们可以重新开始。")))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr));
    }

    @Test
    void chat_multiTurnAmbiguousDateKeepsSpecificFollowUp() throws Exception {
        String sessionId = startRouteFollowUpSession("查上海到北京机票");

        AiChatRequest secondRequest = new AiChatRequest();
        secondRequest.setSessionId(sessionId);
        secondRequest.setMessage("最近几天");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY_CONTINUATION"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.matchLevel").exists());
    }

    @Test
    void chat_weekdayDateWithCompleteRouteCanSearchAndMissingRoutePreservesDate() throws Exception {
        AiChatRequest complete = new AiChatRequest();
        complete.setMessage("上海到北京下周一机票");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value(either(
                        org.hamcrest.Matchers.is("FLIGHT_RECOMMENDATION"))
                        .or(org.hamcrest.Matchers.is("NO_RESULT"))))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").exists());

        AiChatRequest missingRoute = new AiChatRequest();
        missingRoute.setMessage("查询下周一机票");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingRoute)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").exists())
                .andExpect(jsonPath("$.data.flights").isNotEmpty());
    }

    @Test
    void chat_ambiguousDateExpressionsAskForOneDepartureDate() throws Exception {
        for (String message : java.util.List.of("最近几天的机票", "未来一周机票", "周一周二都可以")) {
            AiChatRequest request = new AiChatRequest();
            request.setMessage("上海到北京" + message);

            mockMvc.perform(post("/api/ai/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                    .andExpect(jsonPath("$.data.parsedCondition.departureDate").doesNotExist())
                    .andExpect(jsonPath("$.data.matchLevel").exists());
        }
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
    void chat_directFlightQueryParsesRouteAndSearchesDatabaseOnly() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("广州到北京明天机票");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY"))
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(tomorrowStr))
                .andExpect(jsonPath("$.data.flights").isNotEmpty());
    }

    @Test
    void chat_destinationRecommendRemembersPrimaryCity() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("我想周末看海，预算别太高");

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.intent").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.replyText").value(containsString("三亚")))
                .andReturn();

        ApiResponse<Map> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) response.getData()).get("sessionId");

        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].extra.conversationState.recommendedDestinationCity")
                        .value("三亚"));
    }

    @Test
    void chat_recommendedDestinationSurvivesLaterAssistantWithoutState() throws Exception {
        AiChatRequest first = new AiChatRequest();
        first.setMessage("我想周末看海，预算别太高");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest second = new AiChatRequest();
        second.setSessionId(sessionId);
        second.setMessage("订单在哪看");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("BOOKING_HELP"));

        jdbcTemplate.update("""
                UPDATE ai_chat_message
                SET extra_json = '{"replyType":"BOOKING_HELP","intent":"BOOKING_HELP","parsedCondition":{},"missingFields":[]}'
                WHERE id = (
                    SELECT id FROM (
                        SELECT m.id
                        FROM ai_chat_message m
                        JOIN ai_chat_session s ON s.id = m.session_id
                        WHERE s.public_session_id = ?
                          AND m.role = 'ASSISTANT'
                        ORDER BY m.created_at DESC, m.id DESC
                        LIMIT 1
                    ) latest
                )
                """, sessionId);

        AiChatRequest third = new AiChatRequest();
        third.setSessionId(sessionId);
        third.setMessage("那就去三亚吧，广州出发，下周五");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(third)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("三亚"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate").value(nextIsoWeekday(java.time.DayOfWeek.FRIDAY)));
    }

    @Test
    void chat_mixedDestinationAndFlightNeedRecommendsDestinationAndAsksRemainingSlots() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("推荐一个适合三天玩的地方，并帮我看看机票");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").exists())
                .andExpect(jsonPath("$.data.matchLevel").exists())
                .andExpect(jsonPath("$.data.flights").isNotEmpty());
    }

    @Test
    void chat_recommendedDestinationCanBeOverriddenByUser() throws Exception {
        AiChatRequest first = new AiChatRequest();
        first.setMessage("我想周末看海，预算别太高");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.replyText").value(containsString("三亚")))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest second = new AiChatRequest();
        second.setSessionId(sessionId);
        second.setMessage("不要三亚，换成厦门");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.replyText").value(containsString("厦门")));

        mockMvc.perform(get("/api/ai/sessions/" + sessionId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[3].extra.conversationState.recommendedDestinationCity")
                        .value("厦门"));

        AiChatRequest third = new AiChatRequest();
        third.setSessionId(sessionId);
        third.setMessage("广州出发，下周五");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(third)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("FLIGHT_QUERY"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("广州"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("厦门"))
                .andExpect(jsonPath("$.data.parsedCondition.departureDate")
                        .value(nextIsoWeekday(java.time.DayOfWeek.FRIDAY)));
    }

    @Test
    void chat_afterDestinationRecommendationTravelQuestionStaysTravelChat() throws Exception {
        AiChatRequest first = new AiChatRequest();
        first.setMessage("我想周末看海，预算别太高");

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.replyText").value(containsString("三亚")))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        String sessionId = (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");

        AiChatRequest second = new AiChatRequest();
        second.setSessionId(sessionId);
        second.setMessage("去三亚怎么玩？");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.intent").value("TRAVEL_CHAT"))
                .andExpect(jsonPath("$.data.missingFields").isArray())
                .andExpect(jsonPath("$.data.missingFields").isEmpty())
                .andExpect(jsonPath("$.data.searchUrl").isEmpty())
                .andExpect(jsonPath("$.data.flights").isArray())
                .andExpect(jsonPath("$.data.flights").isEmpty());
    }

    @Test
    void chat_searchUrlIncludesMainFlightFilters() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("从上海到北京" + tomorrowStr + "南航早上两个人经济舱直飞1000以内");

        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andExpect(jsonPath("$.data.parsedCondition.departureCity").value("上海"))
                .andExpect(jsonPath("$.data.parsedCondition.arrivalCity").value("北京"))
                .andExpect(jsonPath("$.data.parsedCondition.passengerCount").value(2))
                .andExpect(jsonPath("$.data.parsedCondition.cabinClass").value("ECONOMY"))
                .andExpect(jsonPath("$.data.parsedCondition.airlineRaw").value("南方航空"))
                .andExpect(jsonPath("$.data.parsedCondition.maxPrice").value(1000))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeStart").value("06:00"))
                .andExpect(jsonPath("$.data.parsedCondition.departureTimeEnd").value("12:00"))
                .andExpect(jsonPath("$.data.parsedCondition.directOnly").value(true))
                .andReturn();

        ApiResponse<Map> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) response.getData();
        String searchUrl = (String) data.get("searchUrl");
        String decodedSearchUrl = java.net.URLDecoder.decode(
                searchUrl, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(decodedSearchUrl)
                .startsWith("/flights?")
                .contains("departureCity=上海")
                .contains("arrivalCity=北京")
                .contains("departureDate=" + tomorrowStr)
                .contains("passengerCount=2")
                .contains("cabinClass=ECONOMY")
                .contains("airlineRaw=南方航空")
                .contains("airlineId=2")
                .contains("maxPrice=1000")
                .contains("departureTimeStart=06:00")
                .contains("departureTimeEnd=12:00")
                .contains("directOnly=true");
    }

    private String startRouteFollowUpSession(String message) throws Exception {
        AiChatRequest firstRequest = new AiChatRequest();
        firstRequest.setMessage(message);

        MvcResult firstResult = mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyType").value("FLIGHT_RECOMMENDATION"))
                .andReturn();

        ApiResponse<Map> firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ApiResponse.class);
        return (String) ((Map<String, Object>) firstResponse.getData()).get("sessionId");
    }

    private String nextIsoWeekday(java.time.DayOfWeek dayOfWeek) {
        LocalDate today = LocalDate.now();
        LocalDate nextWeekMonday = today.plusWeeks(1)
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return nextWeekMonday.plusDays(dayOfWeek.getValue() - java.time.DayOfWeek.MONDAY.getValue()).toString();
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
        dto.setPassword("SkyBooker@Init2026!");

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
                "INSERT IGNORE INTO users(email, password_hash, nickname, role, email_verified) " +
                        "VALUES('user2@test.com', '$2b$12$mxGK3588bVIlwCYgjrqa1.1esZ8vbAKALvroPmpAvJfGt3VO781oy', '测试用户2', 'USER', 1)");

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
