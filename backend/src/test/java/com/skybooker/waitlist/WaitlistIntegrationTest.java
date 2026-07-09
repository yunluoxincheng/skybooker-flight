package com.skybooker.waitlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.order.dto.CreateOrderDTO;
import com.skybooker.waitlist.dto.CreateWaitlistDTO;
import com.skybooker.waitlist.service.WaitlistService;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WaitlistIntegrationTest {

    private static final AtomicLong UNIQUE_SUFFIX = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WaitlistService waitlistService;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        userToken = loginAsUser();
        adminToken = loginAsAdmin();
    }

    // ---- Creation ----

    @Test
    void createWaitlist_success() throws Exception {
        Long flightId = createFlightWithSoldOut();
        Long passengerId = 1L;

        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(passengerId));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(json.get("data").get("payAmount").asDouble()).isGreaterThan(0);
        assertThat(json.get("data").get("passengers").size()).isEqualTo(1);
    }

    @Test
    void createWaitlist_rejectsUnsellableFlight() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().minusDays(1), "PUBLISHED", "ON_TIME");
        mockMvc.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildWaitlistDTO(flightId, "ECONOMY", List.of(1L)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    void createWaitlist_rejectsAvailableInventory() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3), "PUBLISHED", "ON_TIME");
        mockMvc.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildWaitlistDTO(flightId, "ECONOMY", List.of(1L)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50004));
    }

    @Test
    void createWaitlist_rejectsDuplicatePassengers() throws Exception {
        Long flightId = createFlightWithSoldOut();
        mockMvc.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildWaitlistDTO(flightId, "ECONOMY", List.of(1L, 1L)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50005));
    }

    @Test
    void createWaitlist_amountCalculation() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        double payAmount = json.get("data").get("payAmount").asDouble();
        assertThat(payAmount).isEqualTo(500.00 + 50.00 + 30.00);
    }

    // ---- List/Detail ownership ----

    @Test
    void listMyWaitlists_success() throws Exception {
        Long flightId = createFlightWithSoldOut();
        createWaitlist(flightId, "ECONOMY", List.of(1L));

        mockMvc.perform(get("/api/waitlist/my")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getWaitlistDetail_enforcesOwnership() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/waitlist/" + wlId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // ---- Payment ----

    @Test
    void payWaitlist_success() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/waitlist/" + wlId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());
    }

    @Test
    void payWaitlist_idempotentOnWaiting() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/waitlist/" + wlId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        mockMvc.perform(post("/api/waitlist/" + wlId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"));
    }

    @Test
    void payWaitlist_rejectsExpired() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        jdbcTemplate.update("UPDATE waitlist_order SET expire_time = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), wlId);

        mockMvc.perform(post("/api/waitlist/" + wlId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50003));
    }

    // ---- Cancellation ----

    @Test
    void cancelWaitlist_pendingPayment() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/waitlist/" + wlId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelWaitlist_waiting_refundsPayment() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/waitlist/" + wlId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/waitlist/" + wlId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundAmount").isNotEmpty())
                .andExpect(jsonPath("$.data.refundTime").isNotEmpty());
    }

    @Test
    void cancelWaitlist_rejectsSuccess() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        jdbcTemplate.update("UPDATE waitlist_order SET status = 'SUCCESS' WHERE id = ?", wlId);

        mockMvc.perform(post("/api/waitlist/" + wlId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50003));
    }

    @Test
    void cancelWaitlist_idempotentOnCancelled() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/waitlist/" + wlId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/waitlist/" + wlId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    // ---- Timeout cleanup ----

    @Test
    void cleanupExpiredPending_viaService() throws Exception {
        Long flightId = createFlightWithSoldOut();
        MvcResult result = createWaitlist(flightId, "ECONOMY", List.of(1L));
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        jdbcTemplate.update("UPDATE waitlist_order SET expire_time = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE id = ?",
                wlId);

        waitlistService.cleanupExpiredPending();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM waitlist_order WHERE id = ?", String.class, wlId);
        assertThat(status).isEqualTo("EXPIRED");
    }

    // ---- Paid failure cleanup ----

    @Test
    void cleanupUnfulfillableWaiting_viaService() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().minusDays(1), "PUBLISHED", "ON_TIME");
        Long wlId = insertWaitingWaitlist(flightId);

        waitlistService.cleanupUnfulfillableWaiting();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM waitlist_order WHERE id = ?", String.class, wlId);
        assertThat(status).isEqualTo("REFUNDED");

        BigDecimal refundAmount = jdbcTemplate.queryForObject(
                "SELECT refund_amount FROM waitlist_order WHERE id = ?", BigDecimal.class, wlId);
        assertThat(refundAmount).isNotNull();
        assertThat(refundAmount.doubleValue()).isGreaterThan(0);
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
                        .content("{\"username\":\"admin\",\"password\":\"SkyBooker@Init2026!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private Long createFlight(LocalDateTime departure, String publishStatus, String flightStatus) throws Exception {
        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo(nextTestNumber("WL"));
        dto.setAirlineId(1L);
        dto.setDepartureAirportId(1L);
        dto.setArrivalAirportId(3L);
        dto.setDepartureTime(departure);
        dto.setArrivalTime(departure.plusHours(2));
        dto.setDurationMinutes(120);
        dto.setBasePrice(new BigDecimal("500.00"));
        dto.setTotalSeats(12);
        dto.setStatus(flightStatus);
        dto.setPublishStatus(publishStatus);

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

    private Long createFlightWithSoldOut() throws Exception {
        Long flightId = createFlight(LocalDateTime.now().plusDays(3), "PUBLISHED", "ON_TIME");

        jdbcTemplate.update("UPDATE flight_seat SET status = 'SOLD' WHERE flight_id = ? AND status = 'AVAILABLE'",
                flightId);
        jdbcTemplate.update("UPDATE flight SET remaining_seats = 0 WHERE id = ?", flightId);

        return flightId;
    }

    private MvcResult createWaitlist(Long flightId, String cabinClass, List<Long> passengerIds) throws Exception {
        CreateWaitlistDTO dto = buildWaitlistDTO(flightId, cabinClass, passengerIds);
        return mockMvc.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andReturn();
    }

    private CreateWaitlistDTO buildWaitlistDTO(Long flightId, String cabinClass, List<Long> passengerIds) {
        CreateWaitlistDTO dto = new CreateWaitlistDTO();
        dto.setFlightId(flightId);
        dto.setCabinClass(cabinClass);
        dto.setPassengerIds(passengerIds);
        return dto;
    }

    private Long insertWaitingWaitlist(Long flightId) {
        jdbcTemplate.update(
                "INSERT INTO waitlist_order(waitlist_no, user_id, flight_id, passenger_count, cabin_class, pay_amount, status, paid_at) " +
                        "VALUES(?, 1, ?, 1, 'ECONOMY', 580, 'WAITING', NOW())",
                nextTestNumber("WLTEST"), flightId);
        Long wlId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                "INSERT INTO waitlist_passenger(waitlist_id, passenger_id, passenger_name, passenger_type) " +
                        "VALUES(?, 1, '测试乘机人', 'ADULT')", wlId);
        return wlId;
    }

    private String nextTestNumber(String prefix) {
        return prefix + System.currentTimeMillis() + UNIQUE_SUFFIX.incrementAndGet();
    }
}
