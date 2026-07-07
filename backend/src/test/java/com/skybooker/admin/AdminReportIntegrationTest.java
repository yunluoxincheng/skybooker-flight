package com.skybooker.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminReportIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        resetReportData();
        adminToken = loginAsAdmin();
        userToken = loginAsUser();
    }

    @AfterEach
    void tearDown() {
        restoreDemoOrder();
    }

    @Test
    void reportEndpoints_rejectAnonymousAndUserPortalTokens() throws Exception {
        String endpoint = "/api/admin/reports/sales-trend?startDate=2026-06-01&endDate=2026-06-02&granularity=DAY";

        mockMvc.perform(get(endpoint))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void reportFilters_validateDatesGranularityAndLimits() throws Exception {
        mockMvc.perform(get("/api/admin/reports/sales-trend?endDate=2026-06-02&granularity=DAY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/reports/route-performance?startDate=2026-06-03&endDate=2026-06-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(get("/api/admin/reports/refund-trend?startDate=2025-01-01&endDate=2026-02-02&granularity=DAY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(get("/api/admin/reports/sales-trend?startDate=2025-01-01&endDate=2026-01-01&granularity=DAY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(366)));

        mockMvc.perform(get("/api/admin/reports/sales-trend?startDate=2025-01-01&endDate=2026-01-02&granularity=DAY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(get("/api/admin/reports/sales-trend?startDate=2026-06-01&endDate=2026-06-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/reports/refund-trend?startDate=2026-06-01&endDate=2026-06-02&granularity=WEEK")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(get("/api/admin/reports/flight-load-factor?startDate=2026-06-01&endDate=2026-06-02&limit=0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));

        for (int i = 0; i < 55; i++) {
            insertFlight(1L, 1L, 3L, LocalDateTime.of(2026, 6, 1, 6, 0).plusMinutes(i), 10);
        }

        mockMvc.perform(get("/api/admin/reports/flight-load-factor?startDate=2026-06-01&endDate=2026-06-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(20)));

        mockMvc.perform(get("/api/admin/reports/flight-load-factor?startDate=2026-06-01&endDate=2026-06-02&limit=99")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(50)));
    }

    @Test
    void salesTrend_aggregatesActiveOrdersByPayTimeAndFillsEmptyPeriods() throws Exception {
        Long issued = insertTicketOrder("SALES-ISSUED", 1L, "ISSUED", "300.00",
                LocalDateTime.of(2026, 6, 1, 10, 0));
        insertOrderPassenger(issued, 1L, 2);
        Long changed = insertTicketOrder("SALES-CHANGED", 1L, "CHANGED", "200.00",
                LocalDateTime.of(2026, 6, 1, 13, 0));
        insertOrderPassenger(changed, 1L, 1);
        insertTicketOrder("SALES-CANCELLED", 1L, "CANCELLED", "999.00",
                LocalDateTime.of(2026, 6, 1, 14, 0));
        insertTicketOrder("SALES-PENDING", 1L, "PENDING_PAYMENT", "999.00",
                LocalDateTime.of(2026, 6, 1, 15, 0));
        insertTicketOrder("SALES-REFUNDED", 1L, "REFUNDED", "999.00",
                LocalDateTime.of(2026, 6, 1, 16, 0));
        insertTicketOrder("SALES-CHANGE-PENDING", 1L, "CHANGE_PENDING", "999.00",
                LocalDateTime.of(2026, 6, 1, 17, 0));
        insertTicketOrder("SALES-OUTSIDE", 1L, "ISSUED", "500.00",
                LocalDateTime.of(2026, 5, 31, 23, 0));

        mockMvc.perform(get("/api/admin/reports/sales-trend?startDate=2026-06-01&endDate=2026-06-03&granularity=DAY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].period").value("2026-06-01"))
                .andExpect(jsonPath("$.data[0].activeOrderCount").value(2))
                .andExpect(jsonPath("$.data[0].passengerCount").value(3))
                .andExpect(jsonPath("$.data[0].revenue").value(500.00))
                .andExpect(jsonPath("$.data[1].period").value("2026-06-02"))
                .andExpect(jsonPath("$.data[1].activeOrderCount").value(0))
                .andExpect(jsonPath("$.data[1].passengerCount").value(0))
                .andExpect(jsonPath("$.data[1].revenue").value(0.00));

        mockMvc.perform(get("/api/admin/reports/sales-trend?startDate=2026-06-01&endDate=2026-07-31&granularity=MONTH")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].period").value("2026-06"))
                .andExpect(jsonPath("$.data[0].activeOrderCount").value(2))
                .andExpect(jsonPath("$.data[0].revenue").value(500.00))
                .andExpect(jsonPath("$.data[1].period").value("2026-07"))
                .andExpect(jsonPath("$.data[1].activeOrderCount").value(0));
    }

    @Test
    void routePerformance_reportsRevenueRefundsFiltersSortingAndEmptyResults() throws Exception {
        Long shanghaiBeijingOne = insertTicketOrder("ROUTE-SHA-BJS-1", 1L, "ISSUED", "600.00",
                LocalDateTime.of(2026, 6, 2, 9, 0));
        insertOrderPassenger(shanghaiBeijingOne, 1L, 2);
        Long shanghaiBeijingTwo = insertTicketOrder("ROUTE-SHA-BJS-2", 1L, "CHANGED", "300.00",
                LocalDateTime.of(2026, 6, 2, 10, 0));
        insertOrderPassenger(shanghaiBeijingTwo, 1L, 1);
        Long shanghaiBeijingRefunded = insertTicketOrder("ROUTE-SHA-BJS-REFUND", 1L, "REFUNDED", "500.00",
                LocalDateTime.of(2026, 5, 20, 10, 0));
        insertRefund(shanghaiBeijingRefunded, "100.00", LocalDateTime.of(2026, 6, 2, 11, 0));
        insertRefund(shanghaiBeijingRefunded, "999.00", "FAILED", LocalDateTime.of(2026, 6, 2, 11, 30));

        Long shenzhenChengduRefunded = insertTicketOrder("ROUTE-SZX-CTU-REFUND", 5L, "REFUNDED", "450.00",
                LocalDateTime.of(2026, 5, 20, 10, 0));
        insertRefund(shenzhenChengduRefunded, "450.00", LocalDateTime.of(2026, 6, 2, 12, 0));

        mockMvc.perform(get("/api/admin/reports/route-performance?startDate=2026-06-01&endDate=2026-06-03&limit=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].routeLabel").value("上海 - 北京"))
                .andExpect(jsonPath("$.data[0].activeOrderCount").value(2))
                .andExpect(jsonPath("$.data[0].passengerCount").value(3))
                .andExpect(jsonPath("$.data[0].revenue").value(900.00))
                .andExpect(jsonPath("$.data[0].refundAmount").value(100.00))
                .andExpect(jsonPath("$.data[0].netRevenue").value(800.00))
                .andExpect(jsonPath("$.data[1].routeLabel").value("深圳 - 成都"))
                .andExpect(jsonPath("$.data[1].activeOrderCount").value(0))
                .andExpect(jsonPath("$.data[1].passengerCount").value(0))
                .andExpect(jsonPath("$.data[1].revenue").value(0.00))
                .andExpect(jsonPath("$.data[1].refundAmount").value(450.00))
                .andExpect(jsonPath("$.data[1].netRevenue").value(-450.00));

        mockMvc.perform(get("/api/admin/reports/route-performance?startDate=2026-06-01&endDate=2026-06-03&departureCity=深圳&arrivalCity=成都&airlineId=2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].routeLabel").value("深圳 - 成都"));

        mockMvc.perform(get("/api/admin/reports/route-performance?startDate=2026-07-01&endDate=2026-07-03")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void flightLoadFactor_usesDepartureTimeActivePassengersFiltersAndZeroSeatSafety() throws Exception {
        Long soldFlight = insertFlight(1L, 1L, 3L, LocalDateTime.of(2026, 6, 5, 8, 0), 4);
        Long zeroSeatFlight = insertFlight(2L, 6L, 7L, LocalDateTime.of(2026, 6, 5, 9, 0), 0);
        insertFlight(1L, 1L, 3L, LocalDateTime.of(2026, 6, 8, 8, 0), 4);

        Long issued = insertTicketOrder("LOAD-ISSUED", soldFlight, "ISSUED", "200.00",
                LocalDateTime.of(2026, 6, 1, 8, 0));
        insertOrderPassenger(issued, 1L, 1);
        Long changed = insertTicketOrder("LOAD-CHANGED", soldFlight, "CHANGED", "200.00",
                LocalDateTime.of(2026, 6, 1, 8, 30));
        insertOrderPassenger(changed, 1L, 1);
        Long cancelled = insertTicketOrder("LOAD-CANCELLED", soldFlight, "CANCELLED", "200.00",
                LocalDateTime.of(2026, 6, 1, 9, 0));
        insertOrderPassenger(cancelled, 1L, 2);

        mockMvc.perform(get("/api/admin/reports/flight-load-factor?startDate=2026-06-05&endDate=2026-06-05&limit=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].flightId").value(soldFlight))
                .andExpect(jsonPath("$.data[0].soldPassengerCount").value(2))
                .andExpect(jsonPath("$.data[0].totalSeats").value(4))
                .andExpect(jsonPath("$.data[0].loadFactorPercent").value(50.00))
                .andExpect(jsonPath("$.data[1].flightId").value(zeroSeatFlight))
                .andExpect(jsonPath("$.data[1].soldPassengerCount").value(0))
                .andExpect(jsonPath("$.data[1].totalSeats").value(0))
                .andExpect(jsonPath("$.data[1].loadFactorPercent").value(0.00));

        mockMvc.perform(get("/api/admin/reports/flight-load-factor?startDate=2026-06-05&endDate=2026-06-05&departureCity=上海&arrivalCity=北京&airlineId=1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].flightId").value(soldFlight));
    }

    @Test
    void refundTrend_groupsByCreatedAtAndFillsEmptyPeriods() throws Exception {
        Long orderOne = insertTicketOrder("REFUND-TREND-1", 1L, "REFUNDED", "300.00",
                LocalDateTime.of(2026, 5, 20, 10, 0));
        insertRefund(orderOne, "120.00", LocalDateTime.of(2026, 6, 1, 10, 0));
        Long orderTwo = insertTicketOrder("REFUND-TREND-2", 1L, "REFUNDED", "300.00",
                LocalDateTime.of(2026, 5, 20, 10, 0));
        insertRefund(orderTwo, "80.00", LocalDateTime.of(2026, 6, 1, 11, 0));
        insertRefund(orderTwo, "999.00", "FAILED", LocalDateTime.of(2026, 6, 1, 12, 0));
        Long outside = insertTicketOrder("REFUND-TREND-OUTSIDE", 1L, "REFUNDED", "300.00",
                LocalDateTime.of(2026, 5, 20, 10, 0));
        insertRefund(outside, "500.00", LocalDateTime.of(2026, 5, 31, 23, 0));

        mockMvc.perform(get("/api/admin/reports/refund-trend?startDate=2026-06-01&endDate=2026-06-03&granularity=DAY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].period").value("2026-06-01"))
                .andExpect(jsonPath("$.data[0].refundCount").value(2))
                .andExpect(jsonPath("$.data[0].refundAmount").value(200.00))
                .andExpect(jsonPath("$.data[1].period").value("2026-06-02"))
                .andExpect(jsonPath("$.data[1].refundCount").value(0))
                .andExpect(jsonPath("$.data[1].refundAmount").value(0.00));

        mockMvc.perform(get("/api/admin/reports/refund-trend?startDate=2026-06-01&endDate=2026-07-31&granularity=MONTH")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].period").value("2026-06"))
                .andExpect(jsonPath("$.data[0].refundCount").value(2))
                .andExpect(jsonPath("$.data[0].refundAmount").value(200.00))
                .andExpect(jsonPath("$.data[1].period").value("2026-07"))
                .andExpect(jsonPath("$.data[1].refundCount").value(0));
    }

    @Test
    void waitlistPerformance_countsAllStatusesSumsAmountsFiltersAndNormalizesEmptyTotals() throws Exception {
        insertWaitlist("WAIT-PENDING", 1L, "PENDING_PAYMENT", "100.00", null,
                LocalDateTime.of(2026, 6, 4, 8, 0));
        insertWaitlist("WAIT-WAITING", 1L, "WAITING", "110.00", null,
                LocalDateTime.of(2026, 6, 4, 9, 0));
        insertWaitlist("WAIT-SUCCESS", 1L, "SUCCESS", "120.00", null,
                LocalDateTime.of(2026, 6, 4, 10, 0));
        insertWaitlist("WAIT-FAILED", 1L, "FAILED", "130.00", null,
                LocalDateTime.of(2026, 6, 4, 11, 0));
        insertWaitlist("WAIT-CANCELLED", 1L, "CANCELLED", "140.00", null,
                LocalDateTime.of(2026, 6, 4, 12, 0));
        insertWaitlist("WAIT-REFUNDED", 1L, "REFUNDED", "150.00", "50.00",
                LocalDateTime.of(2026, 6, 4, 13, 0));
        insertWaitlist("WAIT-EXPIRED", 1L, "EXPIRED", "160.00", "60.00",
                LocalDateTime.of(2026, 6, 4, 14, 0));
        insertWaitlist("WAIT-OTHER-ROUTE", 5L, "WAITING", "999.00", null,
                LocalDateTime.of(2026, 6, 4, 15, 0));
        insertWaitlist("WAIT-OUTSIDE", 1L, "WAITING", "888.00", null,
                LocalDateTime.of(2026, 6, 5, 0, 0));

        mockMvc.perform(get("/api/admin/reports/waitlist-performance?startDate=2026-06-04&endDate=2026-06-04&departureCity=上海&arrivalCity=北京&airlineId=1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedCount").value(7))
                .andExpect(jsonPath("$.data.pendingPaymentCount").value(1))
                .andExpect(jsonPath("$.data.waitingCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.cancelledCount").value(1))
                .andExpect(jsonPath("$.data.refundedCount").value(1))
                .andExpect(jsonPath("$.data.expiredCount").value(1))
                .andExpect(jsonPath("$.data.payAmount").value(910.00))
                .andExpect(jsonPath("$.data.refundAmount").value(110.00));

        mockMvc.perform(get("/api/admin/reports/waitlist-performance?startDate=2026-07-01&endDate=2026-07-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedCount").value(0))
                .andExpect(jsonPath("$.data.pendingPaymentCount").value(0))
                .andExpect(jsonPath("$.data.waitingCount").value(0))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.cancelledCount").value(0))
                .andExpect(jsonPath("$.data.refundedCount").value(0))
                .andExpect(jsonPath("$.data.expiredCount").value(0))
                .andExpect(jsonPath("$.data.payAmount").value(0.00))
                .andExpect(jsonPath("$.data.refundAmount").value(0.00));
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

    private void resetReportData() {
        jdbcTemplate.update("DELETE FROM waitlist_passenger");
        jdbcTemplate.update("DELETE FROM waitlist_order");
        jdbcTemplate.update("DELETE FROM refund_record");
        jdbcTemplate.update("DELETE FROM change_record");
        jdbcTemplate.update("DELETE FROM order_passenger");
        jdbcTemplate.update("DELETE FROM ticket_order");
        jdbcTemplate.update("DELETE FROM flight WHERE flight_no LIKE 'RPT%'");
    }

    private void restoreDemoOrder() {
        resetReportData();
        jdbcTemplate.update("""
                INSERT INTO ticket_order(id, order_no, user_id, flight_id, status, ticket_amount, airport_fee, fuel_fee, service_fee, total_amount, pay_time, expire_time)
                VALUES(1, 'DEMO202605260001', 2, 1, 'ISSUED', 680.00, 50.00, 30.00, 0.00, 760.00, '2026-05-25 12:00:00', '2026-05-25 12:15:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO order_passenger(order_id, passenger_id, passenger_name, passenger_type, seat_id, seat_no, ticket_price)
                SELECT 1, 1, '张三', 'ADULT', id, seat_no, price
                FROM flight_seat
                WHERE flight_id = 1 AND seat_no = '1A'
                """);
        jdbcTemplate.update("""
                UPDATE flight_seat
                SET status = 'SOLD', locked_by_order_id = 1
                WHERE flight_id = 1 AND seat_no = '1A'
                """);
        jdbcTemplate.update("UPDATE flight SET remaining_seats = 28 WHERE id = 1");
    }

    private Long insertTicketOrder(String orderNoPrefix, Long flightId, String status, String totalAmount,
                                   LocalDateTime payTime) {
        String orderNo = unique(orderNoPrefix);
        BigDecimal amount = new BigDecimal(totalAmount);
        jdbcTemplate.update("""
                INSERT INTO ticket_order(order_no, user_id, flight_id, status, ticket_amount, airport_fee, fuel_fee, service_fee, total_amount, pay_time)
                VALUES(?, 2, ?, ?, ?, 0.00, 0.00, 0.00, ?, ?)
                """, orderNo, flightId, status, amount, amount, payTime);
        return jdbcTemplate.queryForObject("SELECT id FROM ticket_order WHERE order_no = ?", Long.class, orderNo);
    }

    private void insertOrderPassenger(Long orderId, Long flightId, int count) {
        Long seatId = jdbcTemplate.queryForObject(
                "SELECT id FROM flight_seat WHERE flight_id = ? ORDER BY id LIMIT 1", Long.class, flightId);
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update("""
                    INSERT INTO order_passenger(order_id, passenger_id, passenger_name, passenger_type, seat_id, seat_no, ticket_price)
                    VALUES(?, 1, ?, 'ADULT', ?, ?, 100.00)
                    """, orderId, "Report Passenger " + i, seatId, "R" + i);
        }
    }

    private void insertRefund(Long orderId, String refundAmount, LocalDateTime createdAt) {
        insertRefund(orderId, refundAmount, "SUCCESS", createdAt);
    }

    private void insertRefund(Long orderId, String refundAmount, String status, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO refund_record(order_id, user_id, reason, refund_amount, fee_amount, status, created_at)
                VALUES(?, 2, 'admin report test', ?, 0.00, ?, ?)
                """, orderId, new BigDecimal(refundAmount), status, createdAt);
    }

    private void insertWaitlist(String waitlistNoPrefix, Long flightId, String status, String payAmount,
                                String refundAmount, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO waitlist_order(waitlist_no, user_id, flight_id, passenger_count, cabin_class, pay_amount, status, paid_at, refund_amount, created_at)
                VALUES(?, 2, ?, 1, 'ECONOMY', ?, ?, ?, ?, ?)
                """, unique(waitlistNoPrefix), flightId, new BigDecimal(payAmount), status, createdAt,
                refundAmount == null ? null : new BigDecimal(refundAmount), createdAt);
    }

    private Long insertFlight(Long airlineId, Long departureAirportId, Long arrivalAirportId,
                              LocalDateTime departureTime, int totalSeats) {
        String flightNo = unique("RPT");
        jdbcTemplate.update("""
                INSERT INTO flight(flight_no, airline_id, departure_airport_id, arrival_airport_id, departure_time, arrival_time,
                                   duration_minutes, base_price, remaining_seats, total_seats, status, publish_status, direct_flag)
                VALUES(?, ?, ?, ?, ?, ?, 120, 100.00, ?, ?, 'ON_TIME', 'PUBLISHED', 1)
                """, flightNo, airlineId, departureAirportId, arrivalAirportId, departureTime,
                departureTime.plusHours(2), totalSeats, totalSeats);
        return jdbcTemplate.queryForObject("SELECT id FROM flight WHERE flight_no = ?", Long.class, flightNo);
    }

    private String unique(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet() + "-" + System.nanoTime();
    }
}
