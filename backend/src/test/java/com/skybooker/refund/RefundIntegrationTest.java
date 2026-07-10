package com.skybooker.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.order.dto.ChangeOrderDTO;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RefundIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock businessClock;

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
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(refund(orderId, userToken))
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
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusHours(5));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(174.00))
                .andExpect(jsonPath("$.data.refundAmount").value(406.00));
    }

    @Test
    void refund_changedOrder_releasesCurrentChangedSeat() throws Exception {
        Long oldFlightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long newFlightId = createFlight(LocalDateTime.now(businessClock).plusDays(5));
        Long orderId = createAndPayOrder(oldFlightId);
        Long newSeatId = getAvailableSeatId(newFlightId);

        ChangeOrderDTO changeDto = new ChangeOrderDTO();
        changeDto.setNewFlightId(newFlightId);
        ChangeOrderDTO.SeatMapping mapping = new ChangeOrderDTO.SeatMapping();
        mapping.setPassengerId(1L);
        mapping.setNewSeatId(newSeatId);
        changeDto.setSeatMappings(List.of(mapping));

        mockMvc.perform(post("/api/orders/" + orderId + "/change")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGED"));

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.feeAmount").value(58.00))
                .andExpect(jsonPath("$.data.refundAmount").value(522.00));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM ticket_order WHERE id = ?", String.class, orderId);
        assertThat(status).isEqualTo("REFUNDED");
        Integer newRemaining = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, newFlightId);
        assertThat(newRemaining).isEqualTo(12);
        Integer oldRemaining = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, oldFlightId);
        assertThat(oldRemaining).isEqualTo(12);

        var seat = jdbcTemplate.queryForMap(
                "SELECT status, locked_by_order_id, lock_expire_time FROM flight_seat WHERE id = ?", newSeatId);
        assertThat(seat.get("status")).isEqualTo("AVAILABLE");
        assertThat(seat.get("locked_by_order_id")).isNull();
        assertThat(seat.get("lock_expire_time")).isNull();

        Integer refundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_record WHERE order_id = ?", Integer.class, orderId);
        assertThat(refundCount).isEqualTo(1);
    }

    // ---- Boundary: 24h / 2h thresholds ----
    // Create flights far in the future so order+pay succeeds, then UPDATE departure_time
    // right before refund to eliminate time-drift flakiness in slow CI.

    @Test
    void refund_boundary_justOver24h_charges10pct() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now(businessClock).plusHours(24).plusSeconds(30), flightId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(58.00))
                .andExpect(jsonPath("$.data.refundAmount").value(522.00));
    }

    @Test
    void refund_boundary_justUnder24h_charges30pct() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now(businessClock).plusHours(24).minusSeconds(30), flightId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(174.00))
                .andExpect(jsonPath("$.data.refundAmount").value(406.00));
    }

    @Test
    void refund_boundary_justOver2h_allowsRefund() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now(businessClock).plusHours(2).plusSeconds(30), flightId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeAmount").value(174.00))
                .andExpect(jsonPath("$.data.refundAmount").value(406.00));
    }

    @Test
    void refund_boundary_justUnder2h_rejected() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);
        jdbcTemplate.update("UPDATE flight SET departure_time = ? WHERE id = ?",
                LocalDateTime.now(businessClock).plusHours(2).minusMinutes(1), flightId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50001));
    }

    // ---- Rejections ----

    @Test
    void refund_rejectsTooLate() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusHours(1));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50001));
    }

    @Test
    void refund_rejectsNotOwned() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(refund(orderId, adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void refund_rejectsInvalidStates() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createIssuedOrderViaDb(flightId, "PENDING_PAYMENT");

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Long orderId2 = createIssuedOrderViaDb(flightId, "CANCELLED");
        mockMvc.perform(refund(orderId2, userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ---- Idempotency ----

    @Test
    void refund_idempotentOnAlreadyRefunded() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        Integer refundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_record WHERE order_id = ?", Integer.class, orderId);
        assertThat(refundCount).isEqualTo(1);
    }

    // ---- Inventory consistency ----

    @Test
    void refund_releasesSeatsAndIncreasesRemaining() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        Integer soldBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flight_seat WHERE flight_id = ? AND status = 'SOLD'",
                Integer.class, flightId);
        assertThat(soldBefore).isEqualTo(1);

        mockMvc.perform(refund(orderId, userToken))
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

    // ---- H3: 退款按 order_passenger.seat_id 快照释放,不按 orderId 全量 ----

    @Test
    void refund_releasesOnlySnapshotSeats_keepsStaleOrderIdSoldUntouched() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        // 当前订单关联的快照座位(order_passenger.seat_id)
        Long snapshotSeatId = jdbcTemplate.queryForObject(
                "SELECT seat_id FROM order_passenger WHERE order_id = ?", Long.class, orderId);

        // 造脏数据:同航班另一 AVAILABLE 座位被标为 SOLD 且 locked_by_order_id 指向本订单,
        // 模拟 "orderId 名下残留非快照 SOLD"(异常/并发/历史数据)。它不属于 order_passenger 快照。
        Long staleSeatId = jdbcTemplate.queryForObject(
                "SELECT id FROM flight_seat WHERE flight_id = ? AND status = 'AVAILABLE' LIMIT 1",
                Long.class, flightId);
        jdbcTemplate.update(
                "UPDATE flight_seat SET status = 'SOLD', locked_by_order_id = ?, version = version + 1 WHERE id = ?",
                orderId, staleSeatId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // 快照座位应被释放
        var snapshotSeat = jdbcTemplate.queryForMap(
                "SELECT status, locked_by_order_id FROM flight_seat WHERE id = ?", snapshotSeatId);
        assertThat(snapshotSeat.get("status")).isEqualTo("AVAILABLE");
        assertThat(snapshotSeat.get("locked_by_order_id")).isNull();

        // 脏数据座位必须保持 SOLD —— 按 orderId 全量释放会误放它(H3 病灶)
        var staleSeat = jdbcTemplate.queryForMap(
                "SELECT status, locked_by_order_id FROM flight_seat WHERE id = ?", staleSeatId);
        assertThat(staleSeat.get("status")).isEqualTo("SOLD");
        assertThat(staleSeat.get("locked_by_order_id")).isEqualTo(orderId);

        // 余票按 passengerCount(=1) 回补,而非按 orderId 名下 SOLD 数(=2)
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);
        assertThat(remaining).isEqualTo(12);
    }

    // ---- H3: cabinClasses 同样基于快照,脏 SOLD 的舱位不触发候补兑现 ----

    @Test
    void refund_cabinClassesFromSnapshot_excludesStaleSoldCabin() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        Long snapshotSeatId = jdbcTemplate.queryForObject(
                "SELECT seat_id FROM order_passenger WHERE order_id = ?", Long.class, orderId);

        // 造脏数据:同航班另一 AVAILABLE 座位改为 BUSINESS + SOLD + locked_by_order_id=orderId
        // (BUSINESS 与快照座位的 ECONOMY 不同 cabin)
        Long staleSeatId = jdbcTemplate.queryForObject(
                "SELECT id FROM flight_seat WHERE flight_id = ? AND status = 'AVAILABLE' LIMIT 1",
                Long.class, flightId);
        jdbcTemplate.update(
                "UPDATE flight_seat SET cabin_class='BUSINESS', status='SOLD', locked_by_order_id=?, version=version+1 WHERE id=?",
                orderId, staleSeatId);

        // BUSINESS cabin 现仅 1 个 SOLD 脏座位(AVAILABLE=0),满足候补创建条件
        Long businessPaxId = createPassenger("BusinessCabinPax");
        Long businessWlId = createWaitlist(flightId, "BUSINESS", businessPaxId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // 快照 ECONOMY 座位释放;脏 BUSINESS 座位保持 SOLD
        assertThat(seatStatus(snapshotSeatId)).isEqualTo("AVAILABLE");
        assertThat(seatStatus(staleSeatId)).isEqualTo("SOLD");

        // H3 关键:cabinClasses 只含快照座位 cabin(ECONOMY),不含脏座位 cabin(BUSINESS),
        // 故 BUSINESS 候补不应被退款候补兑现触及 —— status 仍 WAITING 且无 skip_reason。
        // 旧代码(按 orderId 全量查 cabin)会把 BUSINESS 纳入并尝试兑现,因 BUSINESS 无 AVAILABLE
        // 座位而写 last_skip_reason("可用座位不足"),此处可区分新旧实现。
        Map<String, Object> wl = jdbcTemplate.queryForMap(
                "SELECT status, last_skip_reason FROM waitlist_order WHERE id = ?", businessWlId);
        assertThat(wl.get("status")).isEqualTo("WAITING");
        assertThat(wl.get("last_skip_reason")).isNull();
    }

    // ---- Reason validation ----

    @Test
    void refund_rejectsMissingReason() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(post("/api/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refund_persistsReason() throws Exception {
        Long flightId = createFlight(LocalDateTime.now(businessClock).plusDays(3));
        Long orderId = createAndPayOrder(flightId);

        mockMvc.perform(refund(orderId, userToken))
                .andExpect(status().isOk());

        String reason = jdbcTemplate.queryForObject(
                "SELECT reason FROM refund_record WHERE order_id = ?", String.class, orderId);
        assertThat(reason).isEqualTo("行程变更");
    }

    // ---- Helpers ----

    private MockHttpServletRequestBuilder refund(Long orderId, String token) {
        return post("/api/orders/" + orderId + "/refund")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"行程变更\"}");
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

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"SkyBooker@Init2026!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private Long createFlight(LocalDateTime departure) throws Exception {
        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo("RF" + System.currentTimeMillis() % 100000 + COUNTER.incrementAndGet());
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

    private String seatStatus(Long seatId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM flight_seat WHERE id = ?", String.class, seatId);
    }

    private Long createPassenger(String name) throws Exception {
        String idCard = "310101" + String.format("%012d",
                Math.floorMod(System.nanoTime(), 1_000_000_000_000L));
        MvcResult result = mockMvc.perform(post("/api/passengers")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"idCardNo\":\"" + idCard
                                + "\",\"passengerType\":\"ADULT\",\"phone\":\"13900001111\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private Long createWaitlist(Long flightId, String cabinClass, Long passengerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightId\":" + flightId
                                + ",\"cabinClass\":\"" + cabinClass + "\""
                                + ",\"passengerIds\":[" + passengerId + "]}"))
                .andExpect(status().isOk())
                .andReturn();
        Long wlId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        mockMvc.perform(post("/api/waitlist/" + wlId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        return wlId;
    }
}
