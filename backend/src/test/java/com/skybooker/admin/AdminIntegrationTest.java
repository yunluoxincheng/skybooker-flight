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
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

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
        dto.setDepartureTime(LocalDateTime.now().plusDays(3));
        dto.setArrivalTime(LocalDateTime.now().plusDays(3).plusHours(2));
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
    void getOrderDetail_notFound() throws Exception {
        mockMvc.perform(get("/api/admin/orders/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCreateOrder_usesTargetUserAndAudits() throws Exception {
        Long flightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
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
    void adminCreateOrder_rejectsPassengerOutsideTargetUser() throws Exception {
        Long targetUserId = createCleanUser(uniqueEmail("target-mismatch"));
        Long flightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
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
        Long flightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
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
        Long flightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
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
        Long refundFlightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
        Long refundOrderId = createAdminOrder(refundFlightId, getAvailableSeatId(refundFlightId));
        String userToken = loginAsUser();
        mockMvc.perform(post("/api/orders/{id}/pay", refundOrderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now().plusHours(1), refundFlightId);

        mockMvc.perform(post("/api/admin/orders/{id}/refund", refundOrderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ops override\",\"force\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(174.00))
                .andExpect(jsonPath("$.data.refundAmount").value(406.00));

        Long oldFlightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
        Long newFlightId = createPublishedFlight(LocalDateTime.now().plusDays(9));
        Long changeOrderId = createAdminOrder(oldFlightId, getAvailableSeatId(oldFlightId));
        mockMvc.perform(post("/api/orders/{id}/pay", changeOrderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now().plusHours(1), oldFlightId);

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
        Long flightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));
        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now().plusHours(1), flightId);

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
        Long oldFlightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
        Long newFlightId = createPublishedFlight(LocalDateTime.now().plusDays(9));
        Long otherRouteFlightId = createPublishedFlight(LocalDateTime.now().plusDays(10));
        jdbcTemplate.update("UPDATE flight SET arrival_airport_id = 2 WHERE id = ?", otherRouteFlightId);
        Long orderId = createAdminOrder(oldFlightId, getAvailableSeatId(oldFlightId));
        mockMvc.perform(post("/api/orders/{id}/pay", orderId)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now().plusHours(1), oldFlightId);

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
    void adminVoidAndAdminNoteOnlyTouchOrderMetadata() throws Exception {
        Long flightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
        Long orderId = createAdminOrder(flightId, getAvailableSeatId(flightId));
        String userToken = loginAsUser();
        Long newFlightId = createPublishedFlight(LocalDateTime.now().plusDays(9));

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
        Long flightId = createPublishedFlight(LocalDateTime.now().plusDays(7));
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
    void disableUserWithActiveOrdersIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/users/2/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40021));
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
