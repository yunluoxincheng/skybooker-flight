package com.skybooker.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.order.dto.CreateOrderDTO;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RefundIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        userToken = loginAsUser();
        adminToken = loginAsAdmin();
    }

    // ---- Success ----

    @Test
    void refund_success_moreThan24h() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.feeAmount").value(58.00))
                .andExpect(jsonPath("$.data.refundAmount").value(522.00));

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);
        assertThat(remaining).isEqualTo(12);
    }

    @Test
    void refund_success_2hTo24h() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusHours(5));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(174.00))
                .andExpect(jsonPath("$.data.refundAmount").value(406.00));
    }

    // ---- Boundary: 24h / 2h thresholds ----
    // Create flights far in the future so order+pay succeeds, then UPDATE departure_time
    // right before refund to eliminate time-drift flakiness in slow CI.

    @Test
    void refund_boundary_justOver24h_charges10pct() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3));
        Long orderId = createAndPayOrder(flightId);
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now().plusHours(24).plusSeconds(30), flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(58.00))
                .andExpect(jsonPath("$.data.refundAmount").value(522.00));
    }

    @Test
    void refund_boundary_justUnder24h_charges30pct() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3));
        Long orderId = createAndPayOrder(flightId);
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now().plusHours(24).minusSeconds(30), flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(174.00))
                .andExpect(jsonPath("$.data.refundAmount").value(406.00));
    }

    @Test
    void refund_boundary_justOver2h_allowsRefund() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3));
        Long orderId = createAndPayOrder(flightId);
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now().plusHours(2).plusSeconds(30), flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(174.00))
                .andExpect(jsonPath("$.data.refundAmount").value(406.00));
    }

    @Test
    void refund_boundary_justUnder2h_rejected() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3));
        Long orderId = createAndPayOrder(flightId);
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now().plusHours(2).minusMinutes(1), flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50001));
    }

    // ---- Rejections ----

    @Test
    void refund_rejectsTooLate() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusHours(1));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50001));
    }

    @Test
    void refund_rejectsNotOwned() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void refund_rejectsInvalidStates() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3));
        Long orderId = createIssuedOrderViaDb(flightId, "PENDING_PAYMENT");

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Long orderId2 = createIssuedOrderViaDb(flightId, "CANCELLED");
        mockMvc.perform(post("/api/orders/" + orderId2 + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ---- Idempotency ----

    @Test
    void refund_idempotentOnAlreadyRefunded() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        Integer refundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_record WHERE order_id = ?", Integer.class, orderId);
        assertThat(refundCount).isEqualTo(1);
    }

    // ---- Inventory consistency ----

    @Test
    void refund_releasesSeatsAndIncreasesRemaining() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        Integer soldBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flight_seat WHERE flight_id = ? AND status = 'SOLD'",
                Integer.class, flightId);
        assertThat(soldBefore).isEqualTo(1);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        Integer soldAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flight_seat WHERE flight_id = ? AND status = 'SOLD'",
                Integer.class, flightId);
        assertThat(soldAfter).isEqualTo(0);

        Integer available = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flight_seat WHERE flight_id = ? AND status = 'AVAILABLE'",
                Integer.class, flightId);
        assertThat(available).isEqualTo(12);
    }

    // ---- Helpers ----

    private String loginAsUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user1@example.com\",\"password\":\"User@123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private Long createFlight(LocalDateTime departure) throws Exception {
        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo("RF" + System.currentTimeMillis() % 100000);
        dto.setAirlineId(1L);
        dto.setDepartureAirportId(1L);
        dto.setArrivalAirportId(3L);
        dto.setDepartureTime(departure);
        dto.setArrivalTime(departure.plusHours(2));
        dto.setDurationMinutes(120);
        dto.setBasePrice(new BigDecimal("500.00"));
        dto.setTotalSeats(12);
        dto.setStatus("ON_TIME");
        dto.setPublishStatus("PUBLISHED");

        MvcResult result = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        Long flightId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        return flightId;
    }

    private Long createAndPayOrder(Long flightId) throws Exception {
        Long seatId = getAvailableSeatId(flightId);

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setPassengerId(1L);
        item.setSeatId(seatId);
        dto.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        return orderId;
    }

    private Long getAvailableSeatId(Long flightId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/flights/" + flightId + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        var seats = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        for (var seat : seats) {
            if ("AVAILABLE".equals(seat.get("status").asText())) {
                return seat.get("id").asLong();
            }
        }
        throw new IllegalStateException("No available seat found");
    }

    private Long createIssuedOrderViaDb(Long flightId, String status) {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'user1@example.com'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO ticket_order(order_no, user_id, flight_id, status, ticket_amount, airport_fee, fuel_fee, service_fee, total_amount) " +
                        "VALUES(?, ?, ?, ?, 500, 50, 30, 0, 580)",
                "TEST" + System.currentTimeMillis(), userId, flightId, status);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
