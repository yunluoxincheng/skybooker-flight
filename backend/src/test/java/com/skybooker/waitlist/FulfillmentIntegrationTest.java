package com.skybooker.waitlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.order.dto.CreateOrderDTO;
import com.skybooker.waitlist.dto.CreateWaitlistDTO;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FulfillmentIntegrationTest {

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

    @Test
    void refund_fulfillsWaitingWaitlist() throws Exception {
        Long flightId = createFlightWithSeats(3);
        List<Long> orderIds = bookAllSeats(flightId, 3);

        Long wlId = createAndWaitlist(flightId, "ECONOMY", List.of(1L));

        mockMvc.perform(post("/api/orders/" + orderIds.get(0) + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        String wlStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM waitlist_order WHERE id = ?", String.class, wlId);
        assertThat(wlStatus).isEqualTo("SUCCESS");

        Long ticketOrderId = jdbcTemplate.queryForObject(
                "SELECT ticket_order_id FROM waitlist_order WHERE id = ?", Long.class, wlId);
        assertThat(ticketOrderId).isNotNull();

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM ticket_order WHERE id = ?", String.class, ticketOrderId);
        assertThat(orderStatus).isEqualTo("ISSUED");
    }

    @Test
    void refund_skipsLargerParty_fulfillsSmaller() throws Exception {
        Long flightId = createFlightWithSeats(3);
        List<Long> orderIds = bookAllSeats(flightId, 3);

        Long passenger2 = createPassenger("候补测试乘机人B");
        Long passenger3 = createPassenger("候补测试乘机人C");
        Long passenger4 = createPassenger("候补测试乘机人D");

        Long largeWlId = createAndWaitlist(flightId, "ECONOMY", List.of(passenger2, passenger3));
        Long smallWlId = createAndWaitlist(flightId, "ECONOMY", List.of(passenger4));

        mockMvc.perform(post("/api/orders/" + orderIds.get(0) + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        String largeStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM waitlist_order WHERE id = ?", String.class, largeWlId);
        assertThat(largeStatus).isEqualTo("WAITING");

        String skipReason = jdbcTemplate.queryForObject(
                "SELECT last_skip_reason FROM waitlist_order WHERE id = ?", String.class, largeWlId);
        assertThat(skipReason).isNotNull();

        String smallStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM waitlist_order WHERE id = ?", String.class, smallWlId);
        assertThat(smallStatus).isEqualTo("SUCCESS");
    }

    @Test
    void refund_remainingSeatsConsistentAfterFulfillment() throws Exception {
        Long flightId = createFlightWithSeats(3);
        List<Long> orderIds = bookAllSeats(flightId, 3);

        Long passenger2 = createPassenger("一致性测试乘机人");
        Long wlId = createAndWaitlist(flightId, "ECONOMY", List.of(passenger2));

        mockMvc.perform(post("/api/orders/" + orderIds.get(0) + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        int available = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flight_seat WHERE flight_id = ? AND status = 'AVAILABLE'",
                Integer.class, flightId);
        int sold = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flight_seat WHERE flight_id = ? AND status = 'SOLD'",
                Integer.class, flightId);
        int remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);

        assertThat(sold).isEqualTo(3);
        assertThat(available).isEqualTo(0);
        assertThat(remaining).isEqualTo(0);
    }

    @Test
    void refund_noWaitlist_allSeatsReturnedToAvailable() throws Exception {
        Long flightId = createFlightWithSeats(3);
        List<Long> orderIds = bookAllSeats(flightId, 3);

        mockMvc.perform(post("/api/orders/" + orderIds.get(0) + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        int available = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flight_seat WHERE flight_id = ? AND status = 'AVAILABLE'",
                Integer.class, flightId);
        assertThat(available).isEqualTo(1);

        int remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);
        assertThat(remaining).isEqualTo(1);
    }

    @Test
    void fulfillment_generatesIssuedOrderWithCorrectAmount() throws Exception {
        Long flightId = createFlightWithSeats(3);
        List<Long> orderIds = bookAllSeats(flightId, 3);

        Long passenger2 = createPassenger("金额测试乘机人");
        Long wlId = createAndWaitlist(flightId, "ECONOMY", List.of(passenger2));

        MvcResult wlResult = mockMvc.perform(get("/api/waitlist/" + wlId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        double payAmount = objectMapper.readTree(wlResult.getResponse().getContentAsString())
                .get("data").get("payAmount").asDouble();

        mockMvc.perform(post("/api/orders/" + orderIds.get(0) + "/refund")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        Long ticketOrderId = jdbcTemplate.queryForObject(
                "SELECT ticket_order_id FROM waitlist_order WHERE id = ?", Long.class, wlId);

        BigDecimal totalAmount = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM ticket_order WHERE id = ?", BigDecimal.class, ticketOrderId);
        assertThat(totalAmount.doubleValue()).isEqualTo(payAmount);

        BigDecimal airportFee = jdbcTemplate.queryForObject(
                "SELECT airport_fee FROM ticket_order WHERE id = ?", BigDecimal.class, ticketOrderId);
        assertThat(airportFee.doubleValue()).isEqualTo(50.00);

        BigDecimal fuelFee = jdbcTemplate.queryForObject(
                "SELECT fuel_fee FROM ticket_order WHERE id = ?", BigDecimal.class, ticketOrderId);
        assertThat(fuelFee.doubleValue()).isEqualTo(30.00);
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

    private Long createFlightWithSeats(int totalSeats) throws Exception {
        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo("FF" + System.currentTimeMillis() % 100000);
        dto.setAirlineId(1L);
        dto.setDepartureAirportId(1L);
        dto.setArrivalAirportId(3L);
        dto.setDepartureTime(LocalDateTime.now().plusDays(3));
        dto.setArrivalTime(LocalDateTime.now().plusDays(3).plusHours(2));
        dto.setDurationMinutes(120);
        dto.setBasePrice(new BigDecimal("500.00"));
        dto.setTotalSeats(totalSeats);
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

    private List<Long> bookAllSeats(Long flightId, int count) throws Exception {
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Long seatId = getAvailableSeatId(flightId);
            Long passengerId = i == 0 ? 1L : createPassenger("bookingP" + i + "_" + System.currentTimeMillis());

            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setFlightId(flightId);
            CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
            item.setPassengerId(passengerId);
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

            orderIds.add(orderId);
        }
        return orderIds;
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
        throw new IllegalStateException("No available seat");
    }

    private Long createPassenger(String name) throws Exception {
        String idCard = "310101" + String.format("%012d", System.nanoTime() % 1000000000000L);

        MvcResult result = mockMvc.perform(post("/api/passengers")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"idCardNo\":\"" + idCard + "\",\"passengerType\":\"ADULT\",\"phone\":\"13900001111\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private Long createAndWaitlist(Long flightId, String cabinClass, List<Long> passengerIds) throws Exception {
        CreateWaitlistDTO dto = new CreateWaitlistDTO();
        dto.setFlightId(flightId);
        dto.setCabinClass(cabinClass);
        dto.setPassengerIds(passengerIds);

        MvcResult result = mockMvc.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
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
