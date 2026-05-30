package com.skybooker.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.order.dto.CreateOrderDTO;
import com.skybooker.passenger.dto.PassengerDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String adminToken;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws Exception {
        userToken = loginAsUser();
        adminToken = loginAsAdmin();
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
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private Long createSellableFlight() throws Exception {
        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo("TEST001");
        dto.setAirlineId(1L);
        dto.setDepartureAirportId(1L);
        dto.setArrivalAirportId(3L);
        dto.setDepartureTime(LocalDateTime.now().plusDays(7));
        dto.setArrivalTime(LocalDateTime.now().plusDays(7).plusHours(2));
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
        throw new IllegalStateException("No available seat found for flight " + flightId);
    }

    private Long getAnotherAvailableSeatId(Long flightId, Long excludeSeatId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/flights/" + flightId + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        var seats = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        for (var seat : seats) {
            if ("AVAILABLE".equals(seat.get("status").asText()) && seat.get("id").asLong() != excludeSeatId) {
                return seat.get("id").asLong();
            }
        }
        throw new IllegalStateException("No second available seat found for flight " + flightId);
    }

    // ---- Create Order ----

    @Test
    void createOrder_singlePassenger() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setPassengerId(1L);
        item.setSeatId(seatId);
        dto.setItems(List.of(item));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.data.ticketAmount").value(500.00))
                .andExpect(jsonPath("$.data.airportFee").value(50.00))
                .andExpect(jsonPath("$.data.fuelFee").value(30.00))
                .andExpect(jsonPath("$.data.totalAmount").value(580.00))
                .andExpect(jsonPath("$.data.passengers").isArray())
                .andExpect(jsonPath("$.data.passengers.length()").value(1))
                .andExpect(jsonPath("$.data.passengers[0].passengerName").isNotEmpty());
    }

    @Test
    void createOrder_multiPassengerDifferentPassengers() throws Exception {
        Long flightId = createSellableFlight();
        Long seat1 = getAvailableSeatId(flightId);
        Long seat2 = getAnotherAvailableSeatId(flightId, seat1);
        Long passenger2 = createTestPassenger("多人测试乘机人A");

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        CreateOrderDTO.OrderItemDTO item1 = new CreateOrderDTO.OrderItemDTO();
        item1.setPassengerId(1L);
        item1.setSeatId(seat1);
        CreateOrderDTO.OrderItemDTO item2 = new CreateOrderDTO.OrderItemDTO();
        item2.setPassengerId(passenger2);
        item2.setSeatId(seat2);
        dto.setItems(List.of(item1, item2));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.ticketAmount").value(1000.00))
                .andExpect(jsonPath("$.data.airportFee").value(100.00))
                .andExpect(jsonPath("$.data.fuelFee").value(60.00))
                .andExpect(jsonPath("$.data.totalAmount").value(1160.00))
                .andExpect(jsonPath("$.data.passengers.length()").value(2));
    }

    @Test
    void createOrder_rejectsDuplicatePassengerInOrder() throws Exception {
        Long flightId = createSellableFlight();
        Long seat1 = getAvailableSeatId(flightId);
        Long seat2 = getAnotherAvailableSeatId(flightId, seat1);

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        CreateOrderDTO.OrderItemDTO item1 = new CreateOrderDTO.OrderItemDTO();
        item1.setPassengerId(1L);
        item1.setSeatId(seat1);
        CreateOrderDTO.OrderItemDTO item2 = new CreateOrderDTO.OrderItemDTO();
        item2.setPassengerId(1L);
        item2.setSeatId(seat2);
        dto.setItems(List.of(item1, item2));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40007));
    }

    @Test
    void createOrder_rejectsSoldSeat() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setPassengerId(1L);
        item.setSeatId(seatId);
        dto.setItems(List.of(item));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(oneOf(30002, 30003)));
    }

    @Test
    void createOrder_duplicateSeat_rejected() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);
        Long passenger2Id = createTestPassenger("重复座位测试乘机人");

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        CreateOrderDTO.OrderItemDTO item1 = new CreateOrderDTO.OrderItemDTO();
        item1.setPassengerId(1L);
        item1.setSeatId(seatId);
        CreateOrderDTO.OrderItemDTO item2 = new CreateOrderDTO.OrderItemDTO();
        item2.setPassengerId(passenger2Id);
        item2.setSeatId(seatId);
        dto.setItems(List.of(item1, item2));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40006));
    }

    @Test
    void createOrder_rejectsExpiredFlight() throws Exception {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(1L);
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setPassengerId(1L);
        item.setSeatId(2L);
        dto.setItems(List.of(item));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30001));
    }

    // ---- Pay ----

    @Test
    void payOrder_success() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);
        Long orderId = createTestOrder(flightId, seatId);

        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.payTime").isNotEmpty());
    }

    @Test
    void payOrder_idempotentOnAlreadyIssued() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);
        Long orderId = createTestOrder(flightId, seatId);

        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"));

        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"));
    }

    // ---- Cancel ----

    @Test
    void cancelOrder_success() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);
        Long orderId = createTestOrder(flightId, seatId);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_idempotentOnAlreadyCancelled() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);
        Long orderId = createTestOrder(flightId, seatId);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_rejectsAlreadyIssued() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);
        Long orderId = createTestOrder(flightId, seatId);

        // Pay first
        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"));

        // Cancel should fail
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ---- List / Detail ----

    @Test
    void listMyOrders_success() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);
        createTestOrder(flightId, seatId);

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getOrderDetail_success() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);
        Long orderId = createTestOrder(flightId, seatId);

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.passengers").isArray())
                .andExpect(jsonPath("$.data.passengers.length()").value(1));
    }

    @Test
    void getOrderDetail_notOwned_returnsNotFound() throws Exception {
        // Seed order id=1 belongs to user id=2; requesting with admin token gets 403
        mockMvc.perform(get("/api/orders/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // Nonexistent order
        mockMvc.perform(get("/api/orders/99999")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    // ---- Auth ----

    @Test
    void orderEndpoints_rejectUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders/1/pay"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    // ---- Concurrency ----

    @Test
    void concurrentSameSeatBooking_atMostOneSucceeds() throws Exception {
        Long flightId = createSellableFlight();
        Long seatId = getAvailableSeatId(flightId);

        // Register a second user for the concurrency test
        Long passenger2 = createTestPassenger("并发测试乘机人");

        CreateOrderDTO dto1 = new CreateOrderDTO();
        dto1.setFlightId(flightId);
        CreateOrderDTO.OrderItemDTO item1 = new CreateOrderDTO.OrderItemDTO();
        item1.setPassengerId(1L);
        item1.setSeatId(seatId);
        dto1.setItems(List.of(item1));

        CreateOrderDTO dto2 = new CreateOrderDTO();
        dto2.setFlightId(flightId);
        CreateOrderDTO.OrderItemDTO item2 = new CreateOrderDTO.OrderItemDTO();
        item2.setPassengerId(passenger2);
        item2.setSeatId(seatId);
        dto2.setItems(List.of(item2));

        String body1 = objectMapper.writeValueAsString(dto1);
        String body2 = objectMapper.writeValueAsString(dto2);

        int concurrency = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        for (int i = 0; i < concurrency; i++) {
            final String body = (i % 2 == 0) ? body1 : body2;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/orders")
                                    .header("Authorization", "Bearer " + userToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn();
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // At most 1 request should succeed for the same seat
        assert successCount.get() <= 1 : "Expected at most 1 success but got " + successCount.get();
    }

    // ---- Helpers ----

    private Long createTestPassenger(String name) throws Exception {
        PassengerDTO dto = new PassengerDTO();
        String idCard = "310101" + String.format("%012d", System.currentTimeMillis() % 1000000000000L + counter.incrementAndGet() * 7);
        dto.setName(name);
        dto.setIdCardNo(idCard);
        dto.setPassengerType("ADULT");
        dto.setPhone("139" + String.format("%08d", counter.get() * 13));

        MvcResult result = mockMvc.perform(post("/api/passengers")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private Long createTestOrder(Long flightId, Long seatId) throws Exception {
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

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }
}
