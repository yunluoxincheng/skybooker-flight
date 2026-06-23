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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminDashboardIntegrationTest {

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
        resetDashboardData();
        adminToken = loginAsAdmin();
        userToken = loginAsUser();
    }

    @AfterEach
    void tearDown() {
        restoreDemoOrder();
    }

    @Test
    void summary_returnsAggregateMetricsAndMonetaryTotals() throws Exception {
        Long expectedTotalUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'USER'", Long.class);
        Long expectedTotalFlights = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flight", Long.class);
        Long issuedOrderOne = insertTicketOrder("SUMMARY-ISSUED-1", 1L, "ISSUED", "500.00");
        Long issuedOrderTwo = insertTicketOrder("SUMMARY-ISSUED-2", 4L, "ISSUED", "400.00");
        insertTicketOrder("SUMMARY-PENDING", 5L, "PENDING_PAYMENT", "200.00");
        Long refundedOrder = insertTicketOrder("SUMMARY-REFUNDED", 1L, "REFUNDED", "300.00");
        insertOrderPassenger(issuedOrderOne, 1L, 1);
        insertOrderPassenger(issuedOrderTwo, 4L, 1);
        insertRefund(refundedOrder, "120.00");
        insertWaitlist("SUMMARY-WAITING", 1L, "WAITING", null);
        insertWaitlist("SUMMARY-REFUNDED", 4L, "REFUNDED", "55.00");

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").value(expectedTotalUsers))
                .andExpect(jsonPath("$.data.totalFlights").value(expectedTotalFlights))
                .andExpect(jsonPath("$.data.totalTicketOrders").value(4))
                .andExpect(jsonPath("$.data.issuedTicketOrders").value(2))
                .andExpect(jsonPath("$.data.refundedTicketOrders").value(1))
                .andExpect(jsonPath("$.data.pendingPaymentTicketOrders").value(1))
                .andExpect(jsonPath("$.data.totalWaitlistOrders").value(2))
                .andExpect(jsonPath("$.data.waitingWaitlistOrders").value(1))
                .andExpect(jsonPath("$.data.grossIssuedOrderRevenue").value(900.00))
                .andExpect(jsonPath("$.data.ticketRefundAmount").value(120.00))
                .andExpect(jsonPath("$.data.waitlistRefundAmount").value(55.00))
                .andExpect(jsonPath("$.data.totalRefundAmount").value(175.00));
    }

    @Test
    void hotRoutes_usesIssuedOrdersOnlyAndSortsByCountThenRevenue() throws Exception {
        Long shanghaiBeijingOne = insertTicketOrder("HOT-SHA-BJS-1", 1L, "ISSUED", "500.00");
        Long shanghaiBeijingTwo = insertTicketOrder("HOT-SHA-BJS-2", 1L, "ISSUED", "400.00");
        Long guangzhouShanghaiOne = insertTicketOrder("HOT-CAN-SHA-1", 4L, "ISSUED", "800.00");
        Long guangzhouShanghaiTwo = insertTicketOrder("HOT-CAN-SHA-2", 4L, "ISSUED", "300.00");
        Long shenzhenChengdu = insertTicketOrder("HOT-SZX-CTU-1", 5L, "ISSUED", "1000.00");
        Long ignoredCancelled = insertTicketOrder("HOT-IGNORED", 5L, "CANCELLED", "9999.00");
        insertOrderPassenger(shanghaiBeijingOne, 1L, 2);
        insertOrderPassenger(shanghaiBeijingTwo, 1L, 1);
        insertOrderPassenger(guangzhouShanghaiOne, 4L, 1);
        insertOrderPassenger(guangzhouShanghaiTwo, 4L, 1);
        insertOrderPassenger(shenzhenChengdu, 5L, 1);
        insertOrderPassenger(ignoredCancelled, 5L, 3);

        mockMvc.perform(get("/api/admin/dashboard/hot-routes?limit=3")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].routeLabel").value("广州 - 上海"))
                .andExpect(jsonPath("$.data[0].issuedOrderCount").value(2))
                .andExpect(jsonPath("$.data[0].passengerCount").value(2))
                .andExpect(jsonPath("$.data[0].revenue").value(1100.00))
                .andExpect(jsonPath("$.data[1].routeLabel").value("上海 - 北京"))
                .andExpect(jsonPath("$.data[1].issuedOrderCount").value(2))
                .andExpect(jsonPath("$.data[1].passengerCount").value(3))
                .andExpect(jsonPath("$.data[1].revenue").value(900.00))
                .andExpect(jsonPath("$.data[2].routeLabel").value("深圳 - 成都"))
                .andExpect(jsonPath("$.data[2].issuedOrderCount").value(1))
                .andExpect(jsonPath("$.data[2].revenue").value(1000.00));
    }

    @Test
    void hotRoutes_appliesDefaultLimitCapAndInvalidLimitValidation() throws Exception {
        insertIssuedOrdersForManyRoutes(25);

        mockMvc.perform(get("/api/admin/dashboard/hot-routes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(lessThanOrEqualTo(10)));

        mockMvc.perform(get("/api/admin/dashboard/hot-routes?limit=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(20)));

        mockMvc.perform(get("/api/admin/dashboard/hot-routes?limit=0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void orderStatusDistribution_returnsPresentStatusesInBusinessOrder() throws Exception {
        insertTicketOrder("STATUS-CHANGED", 1L, "CHANGED", "100.00");
        insertTicketOrder("STATUS-ISSUED", 1L, "ISSUED", "100.00");
        insertTicketOrder("STATUS-PENDING", 1L, "PENDING_PAYMENT", "100.00");
        insertTicketOrder("STATUS-CANCELLED", 1L, "CANCELLED", "100.00");

        mockMvc.perform(get("/api/admin/dashboard/order-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[0].count").value(1))
                .andExpect(jsonPath("$.data[1].status").value("ISSUED"))
                .andExpect(jsonPath("$.data[2].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[3].status").value("CHANGED"));
    }

    @Test
    void dashboardEndpoints_handleEmptyResultSets() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTicketOrders").value(0))
                .andExpect(jsonPath("$.data.grossIssuedOrderRevenue").value(0.00))
                .andExpect(jsonPath("$.data.ticketRefundAmount").value(0.00))
                .andExpect(jsonPath("$.data.waitlistRefundAmount").value(0.00))
                .andExpect(jsonPath("$.data.totalRefundAmount").value(0.00));

        mockMvc.perform(get("/api/admin/dashboard/hot-routes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/admin/dashboard/order-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void dashboardEndpoints_rejectAnonymousAndUserPortalTokens() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
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

    private void resetDashboardData() {
        jdbcTemplate.update("DELETE FROM waitlist_passenger");
        jdbcTemplate.update("DELETE FROM waitlist_order");
        jdbcTemplate.update("DELETE FROM refund_record");
        jdbcTemplate.update("DELETE FROM change_record");
        jdbcTemplate.update("DELETE FROM order_passenger");
        jdbcTemplate.update("DELETE FROM ticket_order");
        jdbcTemplate.update("DELETE FROM flight WHERE flight_no LIKE 'DSH%'");
        jdbcTemplate.update("DELETE FROM airport WHERE code LIKE 'DASH%'");
    }

    private void restoreDemoOrder() {
        resetDashboardData();
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
        jdbcTemplate.update("UPDATE flight SET remaining_seats = 29 WHERE id = 1");
    }

    private Long insertTicketOrder(String orderNo, Long flightId, String status, String totalAmount) {
        BigDecimal amount = new BigDecimal(totalAmount);
        jdbcTemplate.update("""
                INSERT INTO ticket_order(order_no, user_id, flight_id, status, ticket_amount, airport_fee, fuel_fee, service_fee, total_amount)
                VALUES(?, 2, ?, ?, ?, 0.00, 0.00, 0.00, ?)
                """, orderNo, flightId, status, amount, amount);
        return jdbcTemplate.queryForObject("SELECT id FROM ticket_order WHERE order_no = ?", Long.class, orderNo);
    }

    private void insertOrderPassenger(Long orderId, Long flightId, int count) {
        Long seatId = jdbcTemplate.queryForObject(
                "SELECT id FROM flight_seat WHERE flight_id = ? ORDER BY id LIMIT 1", Long.class, flightId);
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update("""
                    INSERT INTO order_passenger(order_id, passenger_id, passenger_name, passenger_type, seat_id, seat_no, ticket_price)
                    VALUES(?, 1, ?, 'ADULT', ?, ?, 100.00)
                    """, orderId, "Dashboard Passenger " + i, seatId, "D" + i);
        }
    }

    private void insertRefund(Long orderId, String refundAmount) {
        jdbcTemplate.update("""
                INSERT INTO refund_record(order_id, user_id, reason, refund_amount, fee_amount, status)
                VALUES(?, 2, 'dashboard test', ?, 0.00, 'SUCCESS')
                """, orderId, new BigDecimal(refundAmount));
    }

    private void insertWaitlist(String waitlistNo, Long flightId, String status, String refundAmount) {
        jdbcTemplate.update("""
                INSERT INTO waitlist_order(waitlist_no, user_id, flight_id, passenger_count, cabin_class, pay_amount, status, paid_at, refund_amount)
                VALUES(?, 2, ?, 1, 'ECONOMY', 580.00, ?, NOW(), ?)
                """, waitlistNo, flightId, status, refundAmount == null ? null : new BigDecimal(refundAmount));
    }

    private void insertIssuedOrdersForManyRoutes(int routeCount) {
        for (int i = 0; i < routeCount; i++) {
            long departureAirportId = insertAirport("D" + i, "DashboardDep" + i);
            long arrivalAirportId = insertAirport("A" + i, "DashboardArr" + i);
            Long flightId = insertFlight(departureAirportId, arrivalAirportId, i);
            insertTicketOrder("LIMIT-ROUTE-" + i, flightId, "ISSUED", String.valueOf(100 + i));
        }
    }

    private Long insertAirport(String codeSuffix, String city) {
        String code = ("DASH" + codeSuffix + System.nanoTime());
        if (code.length() > 20) {
            code = code.substring(0, 20);
        }
        jdbcTemplate.update("""
                INSERT INTO airport(code, name, city, province, status)
                VALUES(?, ?, ?, 'Dashboard', 'ENABLED')
                """, code, city + " Airport", city);
        return jdbcTemplate.queryForObject("SELECT id FROM airport WHERE code = ?", Long.class, code);
    }

    private Long insertFlight(long departureAirportId, long arrivalAirportId, int index) {
        String flightNo = "DSH" + System.nanoTime() + index;
        LocalDateTime departureTime = LocalDateTime.now().plusDays(10).plusHours(index);
        jdbcTemplate.update("""
                INSERT INTO flight(flight_no, airline_id, departure_airport_id, arrival_airport_id, departure_time, arrival_time,
                                   duration_minutes, base_price, remaining_seats, total_seats, status, publish_status, direct_flag)
                VALUES(?, 1, ?, ?, ?, ?, 120, 100.00, 30, 30, 'ON_TIME', 'PUBLISHED', 1)
                """, flightNo, departureAirportId, arrivalAirportId, departureTime, departureTime.plusHours(2));
        return jdbcTemplate.queryForObject("SELECT id FROM flight WHERE flight_no = ?", Long.class, flightNo);
    }
}
