package com.skybooker.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightFormDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock businessClock;

    private String adminToken;
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String RUN_ID = Long.toString(System.currentTimeMillis(), 36);

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAsAdmin();
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"SkyBooker@Init2026!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private String loginAsUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user1@example.com\",\"password\":\"User@123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private FlightFormDTO buildValidFlightForm() {
        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo("TEST" + COUNTER.incrementAndGet());
        dto.setAirlineId(1L);
        dto.setDepartureAirportId(1L);
        dto.setArrivalAirportId(3L);
        dto.setDepartureTime(LocalDateTime.now(businessClock).plusDays(3));
        dto.setArrivalTime(LocalDateTime.now(businessClock).plusDays(3).plusHours(2));
        dto.setDurationMinutes(120);
        dto.setBasePrice(new BigDecimal("500.00"));
        dto.setTotalSeats(12);
        dto.setStatus("ON_TIME");
        dto.setPublishStatus("DRAFT");
        dto.setDirectFlag(true);
        dto.setBaggageAllowance("20kg");
        return dto;
    }

    private Long createPublishedFlight(LocalDateTime departureTime) throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setDepartureTime(departureTime);
        dto.setArrivalTime(departureTime.plusHours(2));
        dto.setPublishStatus("PUBLISHED");

        MvcResult result = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/flights/{id}/generate-seats", flightId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return flightId;
    }

    private Long getAvailableSeatId(Long flightId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/flights/{id}/seats", flightId))
                .andExpect(status().isOk())
                .andReturn();
        for (var seat : objectMapper.readTree(result.getResponse().getContentAsString()).get("data")) {
            if ("AVAILABLE".equals(seat.get("status").asText())) {
                return seat.get("id").asLong();
            }
        }
        throw new IllegalStateException("No available seat for flight " + flightId);
    }

    private Long getAnySeatIdFromDb(Long flightId) {
        return jdbcTemplate.queryForObject("SELECT id FROM flight_seat WHERE flight_id = ? ORDER BY id LIMIT 1",
                Long.class, flightId);
    }

    private Long createAdminOrder(Long flightId, Long seatId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": 2,
                                  "flightId": %d,
                                  "items": [{"passengerId": 1, "seatId": %d}]
                                }
                                """.formatted(flightId, seatId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private Long createIssuedAdminOrder(LocalDateTime departureTime) throws Exception {
        Long flightId = createPublishedFlight(departureTime);
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));
        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk());
        return orderId;
    }

    private Long createCleanUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "User@123456",
                                  "nickname": "Clean User"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("NORMAL"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private Long createPassenger(Long userId, String prefix) {
        jdbcTemplate.update("""
                INSERT INTO passenger(user_id, name, id_card_no, passenger_type, phone)
                VALUES(?, ?, ?, 'ADULT', ?)
                """, userId, prefix + " Passenger", unique("IDCARD"), "13800000000");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM passenger WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, userId);
    }

    private String unique(String prefix) {
        return prefix + "-" + RUN_ID + "-" + COUNTER.incrementAndGet();
    }

    private String uniqueEmail(String prefix) {
        return unique(prefix) + "@example.com";
    }

    private Integer countAudit(Long targetId, String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log WHERE target_id = ? AND action = ?",
                Integer.class, targetId, action);
    }

    // ---- Flight Management ----

    @Test
    void listFlights_returnsAllStatuses() throws Exception {
        mockMvc.perform(get("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(5)));
    }

    @Test
    void listFlights_filterByKeyword() throws Exception {
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(10));
        String flightNo = jdbcTemplate.queryForObject(
                "SELECT flight_no FROM flight WHERE id = ?", String.class, flightId);

        mockMvc.perform(get("/api/admin/flights")
                        .param("keyword", flightNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // keyword 模糊匹配应命中刚创建的航班
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].flightNo").value(flightNo));
    }

    @Test
    void listFlights_blankKeywordBehavesLikeMissingKeyword() throws Exception {
        MvcResult withoutKeyword = mockMvc.perform(get("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult blankKeyword = mockMvc.perform(get("/api/admin/flights")
                        .param("keyword", "   ")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(objectMapper.readTree(blankKeyword.getResponse().getContentAsString())
                .get("data").get("total").asLong())
                .isEqualTo(objectMapper.readTree(withoutKeyword.getResponse().getContentAsString())
                        .get("data").get("total").asLong());
    }

    @Test
    void listFlights_trimsKeywordBeforeSearch() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setFlightNo(unique("MU"));
        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/flights")
                        .param("keyword", "  MU  ")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[?(@.flightNo == '%s')]".formatted(dto.getFlightNo())).isNotEmpty());
    }

    @Test
    void listFlights_rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/admin/flights")
                        .param("page", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(get("/api/admin/flights")
                        .param("size", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(get("/api/admin/flights")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void listFlights_filterByStatusOnlyReturnsMatched() throws Exception {
        mockMvc.perform(get("/api/admin/flights")
                        .param("status", "ON_TIME")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // 返回结果要么为空,要么全是 ON_TIME(不混入其他状态)
                .andExpect(jsonPath("$.data.records[?(@.status != 'ON_TIME')]").isEmpty());
    }

    @Test
    void listFlights_invalidStatusRejected() throws Exception {
        mockMvc.perform(get("/api/admin/flights")
                        .param("status", "BOGUS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void listFlights_invalidDateRejected() throws Exception {
        mockMvc.perform(get("/api/admin/flights")
                        .param("departureDateStart", "not-a-date")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void listFlights_invalidAirlineIdRejected() throws Exception {
        // airlineId=abc 触发 MethodArgumentTypeMismatchException，GlobalExceptionHandler 友好返回 400（不 500）
        mockMvc.perform(get("/api/admin/flights")
                        .param("airlineId", "abc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_success() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.flightNo").value(dto.getFlightNo()))
                .andExpect(jsonPath("$.data.publishStatus").value("DRAFT"));
    }

    @Test
    void createFlight_rejectsSameAirport() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setArrivalAirportId(dto.getDepartureAirportId());

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsArrivalBeforeDeparture() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setArrivalTime(dto.getDepartureTime().minusHours(1));

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsInvalidAirline() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setAirlineId(99999L);

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsInvalidAirport() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setDepartureAirportId(99999L);

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsInvalidStatus() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setStatus("INVALID_STATUS");

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsInvalidPublishStatus() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setPublishStatus("LIVE");

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void updateFlight_success() throws Exception {
        FlightFormDTO createDto = buildValidFlightForm();
        MvcResult createResult = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        FlightFormDTO updateDto = buildValidFlightForm();
        updateDto.setFlightNo("TEST88");
        updateDto.setBasePrice(new BigDecimal("600.00"));

        mockMvc.perform(put("/api/admin/flights/" + flightId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.flightNo").value("TEST88"))
                .andExpect(jsonPath("$.data.basePrice").value(600.00));
    }

    @Test
    void publishAndUnpublishFlight() throws Exception {
        FlightFormDTO createDto = buildValidFlightForm();
        MvcResult createResult = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/unpublish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void generateSeats_success() throws Exception {
        FlightFormDTO createDto = buildValidFlightForm();
        createDto.setTotalSeats(6);
        createDto.setPublishStatus("PUBLISHED");
        MvcResult createResult = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Verify seats created via public endpoint (flight is PUBLISHED)
        mockMvc.perform(get("/api/flights/" + flightId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    void generateSeats_rejectsDuplicateGeneration() throws Exception {
        FlightFormDTO createDto = buildValidFlightForm();
        createDto.setTotalSeats(6);
        MvcResult createResult = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003));
    }

    // ---- Order Management ----

    @Test
    void listOrders_success() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].userId").exists())
                .andExpect(jsonPath("$.data.records[0].userEmail").exists())
                .andExpect(jsonPath("$.data.records[0].userNickname").exists());
    }

    @Test
    void listOrders_filtersByStatusAndOrderNoAndRejectsInvalidStatus() throws Exception {
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));
        String orderNo = jdbcTemplate.queryForObject(
                "SELECT order_no FROM ticket_order WHERE id = ?", String.class, orderId);

        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "PENDING_PAYMENT")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.status != 'PENDING_PAYMENT')]").isEmpty())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/admin/orders")
                        .param("orderNo", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(orderId));

        mockMvc.perform(get("/api/admin/orders")
                        .param("orderNo", "NO_SUCH_ORDER_SHOULD_NOT_MATCH")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));

        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "BOGUS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void listOrders_filtersByUserFlightAndDepartureDate() throws Exception {
        LocalDateTime departureTime = LocalDateTime.now(businessClock).plusDays(12);
        Long flightId = createPublishedFlight(departureTime);
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));
        String flightNo = "UF" + COUNTER.incrementAndGet();
        jdbcTemplate.update("UPDATE flight SET flight_no = ? WHERE id = ?", flightNo, flightId);
        LocalDate departureDate = departureTime.toLocalDate();

        mockMvc.perform(get("/api/admin/orders")
                        .param("userId", "2")
                        .param("flightNo", flightNo)
                        .param("departureDateStart", departureDate.toString())
                        .param("departureDateEnd", departureDate.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[?(@.id == %d)]".formatted(orderId)).isNotEmpty())
                .andExpect(jsonPath("$.data.records[?(@.flightNo != '%s')]".formatted(flightNo)).isEmpty());
    }

    @Test
    void getOrderDetail_success() throws Exception {
        mockMvc.perform(get("/api/admin/orders/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("DEMO202605260001"))
                .andExpect(jsonPath("$.data.passengers").isArray())
                .andExpect(jsonPath("$.data.passengers.length()").value(1));
    }

    @Test
    void getEnhancedOrderDetail_returnsAggregatesAndTimeline() throws Exception {
        mockMvc.perform(get("/api/admin/orders/1/detail")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("DEMO202605260001"))
                .andExpect(jsonPath("$.data.passengers").isArray())
                .andExpect(jsonPath("$.data.refunds").isArray())
                .andExpect(jsonPath("$.data.changes").isArray())
                .andExpect(jsonPath("$.data.timeline").isArray())
                .andExpect(jsonPath("$.data.timeline").isNotEmpty());

        mockMvc.perform(get("/api/admin/orders/99999/detail")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderDetail_notFound() throws Exception {
        mockMvc.perform(get("/api/admin/orders/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCreateOrder_usesTargetUserAndAudits() throws Exception {
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log WHERE target_type = 'ORDER' AND target_id = ? AND action = 'ORDER_CREATE'",
                Integer.class, orderId);
        Integer remainingSeats = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);
        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM ticket_order WHERE id = ?", String.class, orderId);

        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(remainingSeats).isEqualTo(11);
        org.assertj.core.api.Assertions.assertThat(orderStatus).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void adminCreateOrder_acceptsUserIdAliasAndRejectsConflictingAlias() throws Exception {
        Long aliasFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long aliasSeatId = getAvailableSeatId(aliasFlightId);

        mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 2,
                                  "flightId": %d,
                                  "items": [{"passengerId": 1, "seatId": %d}]
                                }
                                """.formatted(aliasFlightId, aliasSeatId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));

        Long conflictUserId = createCleanUser(uniqueEmail("conflict-alias"));
        Long conflictFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(8));
        Long conflictSeatId = getAvailableSeatId(conflictFlightId);
        Integer remainingBefore = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, conflictFlightId);

        mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": 2,
                                  "userId": %d,
                                  "flightId": %d,
                                  "items": [{"passengerId": 1, "seatId": %d}]
                                }
                                """.formatted(conflictUserId, conflictFlightId, conflictSeatId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));

        Integer remainingAfter = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, conflictFlightId);
        String seatStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM flight_seat WHERE id = ?", String.class, conflictSeatId);
        org.assertj.core.api.Assertions.assertThat(remainingAfter).isEqualTo(remainingBefore);
        org.assertj.core.api.Assertions.assertThat(seatStatus).isEqualTo("AVAILABLE");
    }

    @Test
    void adminCreateOrder_rejectsPassengerOutsideTargetUser() throws Exception {
        Long targetUserId = createCleanUser(uniqueEmail("target-mismatch"));
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long seatId = getAvailableSeatId(flightId);

        mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": %d,
                                  "flightId": %d,
                                  "items": [{"passengerId": 1, "seatId": %d}]
                                }
                                """.formatted(targetUserId, flightId, seatId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCreateOrder_rejectsNonNormalOrNonUserTarget() throws Exception {
        Long targetUserId = createCleanUser(uniqueEmail("target-status"));
        Long passengerId = createPassenger(targetUserId, "Target Status");
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long seatId = getAvailableSeatId(flightId);
        String request = """
                {
                  "targetUserId": %d,
                  "flightId": %d,
                  "items": [{"passengerId": %d, "seatId": %d}]
                }
                """;

        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", targetUserId);
        mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.formatted(targetUserId, flightId, passengerId, seatId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10008));

        jdbcTemplate.update("UPDATE users SET status = 'DELETED' WHERE id = ?", targetUserId);
        mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.formatted(targetUserId, flightId, passengerId, seatId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10008));

        jdbcTemplate.update("UPDATE users SET status = 'NORMAL', role = 'ADMIN' WHERE id = ?", targetUserId);
        mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.formatted(targetUserId, flightId, passengerId, seatId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void adminCreateOrder_rejectsUnsellableFlightWithoutInventoryChange() throws Exception {
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long seatId = getAvailableSeatId(flightId);
        jdbcTemplate.update("UPDATE flight SET publish_status = 'DRAFT' WHERE id = ?", flightId);

        mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": 2,
                                  "flightId": %d,
                                  "items": [{"passengerId": 1, "seatId": %d}]
                                }
                                """.formatted(flightId, seatId)))
                .andExpect(status().isBadRequest());

        String seatStatus = jdbcTemplate.queryForObject("SELECT status FROM flight_seat WHERE id = ?",
                String.class, seatId);
        Integer remainingSeats = jdbcTemplate.queryForObject("SELECT remaining_seats FROM flight WHERE id = ?",
                Integer.class, flightId);
        org.assertj.core.api.Assertions.assertThat(seatStatus).isEqualTo("AVAILABLE");
        org.assertj.core.api.Assertions.assertThat(remainingSeats).isEqualTo(12);
    }

    @Test
    void adminRefundAndChangeForceOverrideCutoffAndAudit() throws Exception {
        Long refundFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long refundOrderId = createAdminOrder(refundFlightId, getAvailableSeatId(refundFlightId));
        String userToken = loginAsUser();
        mockMvc.perform(post("/api/orders/{id}/pay", refundOrderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now(businessClock).plusHours(1), refundFlightId);

        mockMvc.perform(post("/api/admin/orders/{id}/refund", refundOrderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ops override\",\"force\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(174.00))
                .andExpect(jsonPath("$.data.refundAmount").value(406.00));

        Long oldFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long newFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(9));
        Long changeOrderId = createAdminOrder(oldFlightId, getAvailableSeatId(oldFlightId));
        mockMvc.perform(post("/api/orders/{id}/pay", changeOrderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now(businessClock).plusHours(1), oldFlightId);

        mockMvc.perform(post("/api/admin/orders/{id}/change", changeOrderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newFlightId": %d,
                                  "seatMappings": [{"passengerId": 1, "newSeatId": %d}],
                                  "reason": "ops change",
                                  "force": true
                                }
                                """.formatted(newFlightId, getAvailableSeatId(newFlightId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGED"));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log WHERE (target_id = ? AND action = 'REFUND_FORCE') " +
                        "OR (target_id = ? AND action = 'CHANGE_FORCE')",
                Integer.class, refundOrderId, changeOrderId);
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void adminRefundHonorsWindowRequiresReasonIsIdempotentAndListsRecords() throws Exception {
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));
        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now(businessClock).plusHours(1), flightId);

        mockMvc.perform(post("/api/admin/orders/{id}/refund", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ordinary window\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/orders/{id}/refund", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\",\"force\":true}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(countAudit(orderId, "REFUND_FORCE")).isZero();
        Integer refundRowsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_record WHERE order_id = ?", Integer.class, orderId);
        org.assertj.core.api.Assertions.assertThat(refundRowsBefore).isZero();

        mockMvc.perform(post("/api/admin/orders/{id}/refund", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ops override\",\"force\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(174.00));

        Integer remainingAfterRefund = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);
        org.assertj.core.api.Assertions.assertThat(remainingAfterRefund).isEqualTo(12);

        mockMvc.perform(post("/api/admin/orders/{id}/refund", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"repeat\",\"force\":true}"))
                .andExpect(status().isOk());

        Integer refundRowsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_record WHERE order_id = ?", Integer.class, orderId);
        org.assertj.core.api.Assertions.assertThat(refundRowsAfter).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(countAudit(orderId, "REFUND_FORCE")).isEqualTo(1);

        mockMvc.perform(get("/api/admin/orders/{id}/refunds", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reason").value("ops override"));
    }

    @Test
    void adminChangeHonorsCutoffRequiresReasonRejectsDifferentRouteAndListsRecords() throws Exception {
        Long oldFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long newFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(9));
        Long otherRouteFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(10));
        jdbcTemplate.update("UPDATE flight SET arrival_airport_id = 2 WHERE id = ?", otherRouteFlightId);
        Long orderId = createAdminOrder(oldFlightId, getAvailableSeatId(oldFlightId));
        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now(businessClock).plusHours(1), oldFlightId);

        String validMapping = """
                {
                  "newFlightId": %d,
                  "seatMappings": [{"passengerId": 1, "newSeatId": %d}],
                  "reason": "%s",
                  "force": %s
                }
                """;

        mockMvc.perform(post("/api/admin/orders/{id}/change", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMapping.formatted(newFlightId, getAvailableSeatId(newFlightId), "ordinary cutoff", false)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/orders/{id}/change", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMapping.formatted(newFlightId, getAvailableSeatId(newFlightId), "", true)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/orders/{id}/change", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMapping.formatted(otherRouteFlightId, getAvailableSeatId(otherRouteFlightId), "wrong route", true)))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(countAudit(orderId, "CHANGE_FORCE")).isZero();

        Long newSeatId = getAvailableSeatId(newFlightId);
        mockMvc.perform(post("/api/admin/orders/{id}/change", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMapping.formatted(newFlightId, newSeatId, "ops change", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGED"));

        BigDecimal changeFee = jdbcTemplate.queryForObject(
                "SELECT change_fee FROM change_record WHERE order_id = ?", BigDecimal.class, orderId);
        org.assertj.core.api.Assertions.assertThat(changeFee).isEqualByComparingTo("174.00");

        mockMvc.perform(get("/api/admin/orders/{id}/changes", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].newSeatId").value(newSeatId));
    }

    @Test
    void adminHelperReads_returnPassengersSeatsAndChangeOptions() throws Exception {
        Long oldFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long newFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(9));
        Long orderId = createAdminOrder(oldFlightId, getAvailableSeatId(oldFlightId));
        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/orders/{id}/change-options", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.flightId == %d)]".formatted(newFlightId)).isNotEmpty());

        mockMvc.perform(get("/api/admin/passengers")
                        .param("userId", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[0].passengerType").value("ADULT"));

        mockMvc.perform(get("/api/admin/passengers")
                        .param("userId", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));

        mockMvc.perform(get("/api/admin/flights/{id}/seats", oldFlightId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[0].seatNo").exists());

        String userToken = loginAsUser();
        mockMvc.perform(get("/api/admin/passengers")
                        .param("userId", "2")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/flights/{id}/seats", oldFlightId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminVoidAndAdminNoteOnlyTouchOrderMetadata() throws Exception {
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));
        String userToken = loginAsUser();
        Long newFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(9));

        mockMvc.perform(patch("/api/admin/orders/{id}/admin-note", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNote\":\"manual review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminNote").value("manual review"));

        mockMvc.perform(post("/api/admin/orders/{id}/void", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"not terminal\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40020));

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        Integer remainingBeforeVoid = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);

        mockMvc.perform(post("/api/admin/orders/{id}/void", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"terminal cleanup\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"));

        Integer remainingAfterVoid = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);
        org.assertj.core.api.Assertions.assertThat(remainingAfterVoid).isEqualTo(remainingBeforeVoid);

        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders/{id}/refund", orderId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"voided\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders/{id}/change", orderId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newFlightId": %d,
                                  "seatMappings": [{"passengerId": 1, "newSeatId": %d}]
                                }
                                """.formatted(newFlightId, getAvailableSeatId(newFlightId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminDeleteOrderAsVoidNeverHardDeletesAndValidatesInputs() throws Exception {
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));
        String userToken = loginAsUser();
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        Integer remainingBeforeDelete = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);

        mockMvc.perform(delete("/api/admin/orders/{id}", orderId)
                        .param("type", "delete")
                        .param("reason", "ops cleanup")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"));

        Integer orderRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_order WHERE id = ?", Integer.class, orderId);
        Integer passengerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_passenger WHERE order_id = ?", Integer.class, orderId);
        Integer remainingAfterDelete = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);
        org.assertj.core.api.Assertions.assertThat(orderRows).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(passengerRows).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(remainingAfterDelete).isEqualTo(remainingBeforeDelete);
        org.assertj.core.api.Assertions.assertThat(countAudit(orderId, "VOID")).isEqualTo(1);

        Long pendingFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(8));
        Long pendingOrderId = createAdminOrder(pendingFlightId, getAvailableSeatId(pendingFlightId));
        mockMvc.perform(delete("/api/admin/orders/{id}", pendingOrderId)
                        .param("type", "delete")
                        .param("reason", "not terminal")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40020));
        org.assertj.core.api.Assertions.assertThat(countAudit(pendingOrderId, "VOID")).isZero();

        Long missingReasonFlightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(9));
        Long missingReasonOrderId = createAdminOrder(missingReasonFlightId, getAvailableSeatId(missingReasonFlightId));
        mockMvc.perform(post("/api/orders/{id}/cancel", missingReasonOrderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/orders/{id}", missingReasonOrderId)
                        .param("type", "delete")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(delete("/api/admin/orders/{id}", missingReasonOrderId)
                        .param("type", "hard")
                        .param("reason", "unsupported")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void corsPreflightAllowsPatchForAdminNote() throws Exception {
        mockMvc.perform(options("/api/admin/orders/1/admin-note")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }

    @Test
    void checkConstraintsAllowDeletedUsersAndVoidedOrders() throws Exception {
        Long userId = createCleanUser(uniqueEmail("check-status"));
        Long flightId = createPublishedFlight(LocalDateTime.now(businessClock).plusDays(7));
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));

        jdbcTemplate.update("UPDATE users SET status = 'DELETED' WHERE id = ?", userId);
        jdbcTemplate.update("UPDATE ticket_order SET status = 'VOIDED' WHERE id = ?", orderId);

        String userStatus = jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?",
                String.class, userId);
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM ticket_order WHERE id = ?",
                String.class, orderId);
        org.assertj.core.api.Assertions.assertThat(userStatus).isEqualTo("DELETED");
        org.assertj.core.api.Assertions.assertThat(orderStatus).isEqualTo("VOIDED");
    }

    // ---- User Management ----

    @Test
    void listUsers_success() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist())
                // DELETED 用户不展示在列表中(硬删不再产生 DELETED,此断言兼顾历史残留)
                .andExpect(jsonPath("$.data.records[?(@.status == 'DELETED')]").isEmpty());
    }

    @Test
    void listUsers_filtersByEmailOrNickname() throws Exception {
        String emailKeyword = unique("user-search-email");
        String nicknameKeyword = unique("user-search-nickname");
        jdbcTemplate.update("""
                INSERT INTO users(email, password_hash, nickname, role, status, email_verified, phone_verified)
                VALUES(?, 'hash', ?, 'USER', 'NORMAL', 0, 0)
                """, emailKeyword + "@example.com", nicknameKeyword);

        mockMvc.perform(get("/api/admin/users").param("email", emailKeyword)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].email").value(emailKeyword + "@example.com"));

        mockMvc.perform(get("/api/admin/users").param("keyword", nicknameKeyword)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].nickname").value(nicknameKeyword));
    }

    @Test
    void listUsers_filtersByNicknameStatusAndCombinedConditions() throws Exception {
        String normalEmail = unique("user-filter-normal") + "@example.com";
        String normalNickname = unique("user-filter-nickname");
        String deletedEmail = unique("user-filter-deleted") + "@example.com";
        String adminEmail = unique("user-filter-admin") + "@example.com";
        String adminKeyword = unique("admin-only-keyword");
        jdbcTemplate.update("""
                INSERT INTO users(email, password_hash, nickname, role, status, email_verified, phone_verified)
                VALUES (?, 'hash', ?, 'USER', 'NORMAL', 0, 0),
                       (?, 'hash', 'Deleted User', 'USER', 'DELETED', 0, 0),
                       (?, 'hash', ?, 'ADMIN', 'NORMAL', 0, 0)
                """, normalEmail, normalNickname, deletedEmail, adminEmail, adminKeyword);

        mockMvc.perform(get("/api/admin/users").param("nickname", normalNickname)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].email").value(normalEmail));

        mockMvc.perform(get("/api/admin/users").param("status", "DELETED").param("email", deletedEmail)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].email").value(deletedEmail));

        mockMvc.perform(get("/api/admin/users")
                        .param("keyword", normalNickname.substring(0, 8))
                        .param("email", normalEmail.substring(0, normalEmail.indexOf('@')))
                        .param("nickname", normalNickname)
                        .param("status", "NORMAL")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].email").value(normalEmail));

        mockMvc.perform(get("/api/admin/users").param("keyword", adminKeyword)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/admin/users").param("status", "UNKNOWN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void disableAndEnableUser() throws Exception {
        String email = uniqueEmail("disable-target");
        MvcResult createResult = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "User@123456",
                                  "nickname": "Disable Target"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("NORMAL"))
                .andReturn();
        long userId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/users/{id}/disable", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Disabled user login returns 403 (ACCOUNT_DISABLED)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"User@123456\"}".formatted(email)))
                .andExpect(status().isForbidden());

        // Re-enable
        mockMvc.perform(post("/api/admin/users/{id}/enable", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Should be able to login again
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"User@123456\"}".formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void createUserFixesRoleRejectsDuplicateEmailDoesNotSendVerificationAndAudits() throws Exception {
        String email = uniqueEmail("role-ignore");
        MvcResult createResult = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "User@123456",
                                  "nickname": "Role Ignore",
                                  "role": "ADMIN"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();
        Long userId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "User@123456",
                                  "nickname": "Duplicate"
                                }
                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10009));

        Integer verificationLogs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_verification_code_log WHERE target = ?", Integer.class, email);
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        org.assertj.core.api.Assertions.assertThat(verificationLogs).isZero();
        org.assertj.core.api.Assertions.assertThat(passwordHash).startsWith("$2");
        org.assertj.core.api.Assertions.assertThat(countAudit(userId, "USER_CREATE")).isEqualTo(1);
    }

    @Test
    void deleteUserHardDeletesAndCannotBeEnabledOrLogin() throws Exception {
        String email = uniqueEmail("delete-target");
        Long userId = createCleanUser(email);

        mockMvc.perform(delete("/api/admin/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        // 硬删除:记录被物理清除(而非 status='DELETED'),邮箱随记录释放
        Integer remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class, userId);
        org.assertj.core.api.Assertions.assertThat(remaining).isZero();
        Integer emailTaken = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class, email);
        org.assertj.core.api.Assertions.assertThat(emailTaken).isZero();

        // 记录不存在:enable 走 RESOURCE_NOT_FOUND(404);登录用户不存在返回 401
        mockMvc.perform(post("/api/admin/users/{id}/enable", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"User@123456\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUserRejectsBusinessRecordsAndAdminAccounts() throws Exception {
        // 硬删除:任何业务引用(FK RESTRICT)都阻断,统一返回 USER_HAS_BUSINESS_DATA(40024)。
        Long activeUserId = createCleanUser(uniqueEmail("active-business"));
        jdbcTemplate.update("""
                INSERT INTO ticket_order(order_no, user_id, flight_id, status, total_amount)
                VALUES(?, ?, 1, 'ISSUED', 580.00)
                """, unique("ACTIVE"), activeUserId);
        mockMvc.perform(delete("/api/admin/users/{id}", activeUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40024));

        Long waitlistUserId = createCleanUser(uniqueEmail("waitlist-business"));
        jdbcTemplate.update("""
                INSERT INTO waitlist_order(waitlist_no, user_id, flight_id, passenger_count, cabin_class, pay_amount, status, paid_at)
                VALUES(?, ?, 1, 1, 'ECONOMY', 580.00, 'WAITING', NOW())
                """, unique("WAIT"), waitlistUserId);
        mockMvc.perform(delete("/api/admin/users/{id}", waitlistUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40024));

        Long pendingWaitlistUserId = createCleanUser(uniqueEmail("pending-waitlist-business"));
        jdbcTemplate.update("""
                INSERT INTO waitlist_order(waitlist_no, user_id, flight_id, passenger_count, cabin_class, pay_amount, status)
                VALUES(?, ?, 1, 1, 'ECONOMY', 580.00, 'PENDING_PAYMENT')
                """, unique("WAIT-PENDING"), pendingWaitlistUserId);
        mockMvc.perform(delete("/api/admin/users/{id}", pendingWaitlistUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40024));

        Long processingUserId = createCleanUser(uniqueEmail("processing-business"));
        jdbcTemplate.update("""
                INSERT INTO ticket_order(order_no, user_id, flight_id, status, total_amount)
                VALUES(?, ?, 1, 'REFUNDED', 580.00)
                """, unique("PROCESS"), processingUserId);
        Long processingOrderId = jdbcTemplate.queryForObject(
                "SELECT id FROM ticket_order WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, processingUserId);
        jdbcTemplate.update("""
                INSERT INTO refund_record(order_id, user_id, reason, refund_amount, fee_amount, status)
                VALUES(?, ?, 'manual', 0.00, 0.00, 'PROCESSING')
                """, processingOrderId, processingUserId);
        mockMvc.perform(delete("/api/admin/users/{id}", processingUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40024));

        mockMvc.perform(delete("/api/admin/users/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void deleteCheck_returnsBlockReasonsForUserWithBusinessData() throws Exception {
        Long userId = createCleanUser(uniqueEmail("check-blocked"));
        jdbcTemplate.update("""
                INSERT INTO ticket_order(order_no, user_id, flight_id, status, total_amount)
                VALUES(?, ?, 1, 'ISSUED', 580.00)
                """, unique("CHK"), userId);

        mockMvc.perform(get("/api/admin/users/{id}/delete-check", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(false))
                .andExpect(jsonPath("$.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.blockReasons").isArray())
                .andExpect(jsonPath("$.data.blockReasons[0]").value(containsString("订单")));
    }

    @Test
    void deleteCheck_returnsCanDeleteForCleanUser() throws Exception {
        Long userId = createCleanUser(uniqueEmail("check-clean"));

        mockMvc.perform(get("/api/admin/users/{id}/delete-check", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(true))
                .andExpect(jsonPath("$.data.orderCount").value(0))
                .andExpect(jsonPath("$.data.blockReasons").isEmpty());
    }

    @Test
    void deleteCheck_protectsAdminAccount() throws Exception {
        mockMvc.perform(get("/api/admin/users/1/delete-check")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void deleteCheck_notFound() throws Exception {
        mockMvc.perform(get("/api/admin/users/99999/delete-check")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCheck_blocksOnAiChatSession() throws Exception {
        // ai_chat_session.user_id FK RESTRICT，用过 AI 助手的用户不能硬删
        Long userId = createCleanUser(uniqueEmail("ai-session"));
        jdbcTemplate.update(
                "INSERT INTO ai_chat_session(public_session_id, user_id, status) VALUES(?, ?, 'ACTIVE')",
                unique("AISESS"), userId);

        mockMvc.perform(get("/api/admin/users/{id}/delete-check", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(false))
                .andExpect(jsonPath("$.data.aiSessionCount").value(1))
                .andExpect(jsonPath("$.data.blockReasons").isArray());

        mockMvc.perform(delete("/api/admin/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40024));
    }

    @Test
    void deleteCheck_blocksOnAiRecommendation() throws Exception {
        Long userId = createCleanUser(uniqueEmail("ai-rec"));
        // recommendation.session_id 是 NOT NULL FK → 先建一个 user_id=NULL 的匿名 session 锚定，
        // 这样 aiSessionCount 保持 0，隔离验证 recommendation 的阻断
        jdbcTemplate.update(
                "INSERT INTO ai_chat_session(public_session_id, user_id, status) VALUES(?, NULL, 'ACTIVE')",
                unique("ANON"));
        Long sessionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO ai_recommendation_record(session_id, user_id, query_text) VALUES(?, ?, 'test')",
                sessionId, userId);

        mockMvc.perform(get("/api/admin/users/{id}/delete-check", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(false))
                .andExpect(jsonPath("$.data.aiSessionCount").value(0))
                .andExpect(jsonPath("$.data.aiRecommendationCount").value(1))
                .andExpect(jsonPath("$.data.blockReasons").isArray());

        mockMvc.perform(delete("/api/admin/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40024));
    }

    @Test
    void disableUserAllowsActiveBusinessAndRejectsAuthenticationWhileDisabled() throws Exception {
        String email = uniqueEmail("disable-active-business");
        Long userId = createCleanUser(email);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"User@123456\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        var loginData = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("data");
        String accessToken = loginData.get("accessToken").asText();
        String refreshToken = loginData.get("refreshToken").asText();

        jdbcTemplate.update("""
                INSERT INTO ticket_order(order_no, user_id, flight_id, status, total_amount)
                VALUES(?, ?, 1, 'PENDING_PAYMENT', 580.00), (?, ?, 1, 'ISSUED', 680.00)
                """, unique("PENDING"), userId, unique("ISSUED"), userId);
        Long issuedOrderId = jdbcTemplate.queryForObject("""
                SELECT id FROM ticket_order
                WHERE user_id = ? AND status = 'ISSUED'
                ORDER BY id DESC LIMIT 1
                """, Long.class, userId);
        jdbcTemplate.update("UPDATE ticket_order SET pay_time = NOW() WHERE id = ?", issuedOrderId);
        jdbcTemplate.update("""
                INSERT INTO waitlist_order(waitlist_no, user_id, flight_id, passenger_count, cabin_class, pay_amount, status, paid_at)
                VALUES(?, ?, 1, 1, 'ECONOMY', 580.00, 'WAITING', NOW())
                """, unique("WAIT"), userId);
        jdbcTemplate.update("""
                INSERT INTO refund_record(order_id, user_id, reason, refund_amount, fee_amount, status)
                VALUES(?, ?, 'manual', 0.00, 0.00, 'PROCESSING')
                """, issuedOrderId, userId);
        jdbcTemplate.update("""
                INSERT INTO change_record(order_id, old_flight_id, new_flight_id, price_diff, change_fee, status)
                VALUES(?, 1, 2, 0.00, 0.00, 'PROCESSING')
                """, issuedOrderId);

        String orderStateAndPaymentBefore = jdbcTemplate.queryForObject("""
                SELECT GROUP_CONCAT(CONCAT(status, ':', total_amount, ':', COALESCE(DATE_FORMAT(pay_time, '%Y-%m-%d %H:%i:%s'), 'NULL')) ORDER BY id)
                FROM ticket_order WHERE user_id = ?
                """, String.class, userId);
        String waitlistStatusesBefore = jdbcTemplate.queryForObject(
                "SELECT GROUP_CONCAT(status ORDER BY id) FROM waitlist_order WHERE user_id = ?", String.class, userId);
        String refundStatusesBefore = jdbcTemplate.queryForObject(
                "SELECT GROUP_CONCAT(status ORDER BY id) FROM refund_record WHERE user_id = ?", String.class, userId);
        String changeStatusesBefore = jdbcTemplate.queryForObject(
                "SELECT GROUP_CONCAT(status ORDER BY id) FROM change_record WHERE order_id = ?", String.class, issuedOrderId);
        String seatStatusesBefore = jdbcTemplate.queryForObject(
                "SELECT GROUP_CONCAT(status ORDER BY id) FROM flight_seat WHERE flight_id = 1", String.class);
        Integer totalSeatsBefore = jdbcTemplate.queryForObject(
                "SELECT total_seats FROM flight WHERE id = 1", Integer.class);

        mockMvc.perform(post("/api/admin/users/{id}/disable", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE id = ?", String.class, userId)).isEqualTo("DISABLED");
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                """
                SELECT GROUP_CONCAT(CONCAT(status, ':', total_amount, ':', COALESCE(DATE_FORMAT(pay_time, '%Y-%m-%d %H:%i:%s'), 'NULL')) ORDER BY id)
                FROM ticket_order WHERE user_id = ?
                """, String.class, userId)).isEqualTo(orderStateAndPaymentBefore);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT GROUP_CONCAT(status ORDER BY id) FROM waitlist_order WHERE user_id = ?", String.class, userId)).isEqualTo(waitlistStatusesBefore);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT GROUP_CONCAT(status ORDER BY id) FROM refund_record WHERE user_id = ?", String.class, userId)).isEqualTo(refundStatusesBefore);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT GROUP_CONCAT(status ORDER BY id) FROM change_record WHERE order_id = ?", String.class, issuedOrderId)).isEqualTo(changeStatusesBefore);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT GROUP_CONCAT(status ORDER BY id) FROM flight_seat WHERE flight_id = 1", String.class)).isEqualTo(seatStatusesBefore);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT total_seats FROM flight WHERE id = 1", Integer.class)).isEqualTo(totalSeatsBefore);
        org.assertj.core.api.Assertions.assertThat(countAudit(userId, "USER_DISABLE")).isEqualTo(1);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"User@123456\"}".formatted(email)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/users/{id}/disable", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10008));
        org.assertj.core.api.Assertions.assertThat(countAudit(userId, "USER_DISABLE")).isEqualTo(1);

        mockMvc.perform(post("/api/admin/users/{id}/enable", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk());
    }

    @Test
    void disableUser_protectsAdminAccount() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void enableUser_protectsAdminAccount() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/enable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));
    }

    // ---- Auth Isolation ----

    @Test
    void adminEndpoints_rejectUserToken() throws Exception {
        String userToken = loginAsUser();

        mockMvc.perform(get("/api/admin/flights")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_rejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/flights"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
