package com.skybooker.change;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.order.dto.ChangeOrderDTO;
import com.skybooker.order.dto.CreateOrderDTO;
import com.skybooker.passenger.dto.PassengerDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ChangeIntegrationTest extends com.skybooker.common.AbstractIntegrationTest {

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
        userToken = loginAsUser("user1@example.com", "User@123456");
        adminToken = loginAsAdmin();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM change_record");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'change-other-%@example.com'");
    }

    @Test
    void changeOrder_success_updatesOrderInventorySeatsAndRecords() throws Exception {
        Long oldFlightId = createFlight("OLD", 1L, 3L, LocalDateTime.now(businessClock).plusDays(3),
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
        Long newFlightId = createFlight("NEW", 1L, 3L, LocalDateTime.now(businessClock).plusDays(5),
                new BigDecimal("700.00"), "DELAYED", "PUBLISHED", 12);
        Long passengerTwo = createPassenger("Change Success Passenger");
        List<Long> oldSeatIds = getAvailableSeatIds(oldFlightId, 2);
        Long orderId = createAndPayOrder(oldFlightId, List.of(1L, passengerTwo), oldSeatIds);
        List<Long> newSeatIds = getAvailableSeatIds(newFlightId, 2);

        ChangeOrderDTO dto = changeDto(newFlightId, List.of(
                mapping(1L, newSeatIds.get(0)),
                mapping(passengerTwo, newSeatIds.get(1))));

        mockMvc.perform(post("/api/orders/" + orderId + "/change")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("CHANGED"))
                .andExpect(jsonPath("$.data.flightId").value(newFlightId))
                .andExpect(jsonPath("$.data.totalAmount").value(1560.00))
                .andExpect(jsonPath("$.data.passengers.length()").value(2));

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status, flight_id, ticket_amount, airport_fee, fuel_fee, service_fee, total_amount " +
                        "FROM ticket_order WHERE id = ?", orderId);
        assertThat(order.get("status")).isEqualTo("CHANGED");
        assertThat(((Number) order.get("flight_id")).longValue()).isEqualTo(newFlightId);
        assertThat((BigDecimal) order.get("ticket_amount")).isEqualByComparingTo("1400.00");
        assertThat((BigDecimal) order.get("airport_fee")).isEqualByComparingTo("100.00");
        assertThat((BigDecimal) order.get("fuel_fee")).isEqualByComparingTo("60.00");
        assertThat((BigDecimal) order.get("service_fee")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) order.get("total_amount")).isEqualByComparingTo("1560.00");

        List<Long> currentPassengerSeats = passengerSeatIds(orderId);
        assertThat(currentPassengerSeats).containsExactlyElementsOf(newSeatIds);
        assertThat(remainingSeats(oldFlightId)).isEqualTo(12);
        assertThat(remainingSeats(newFlightId)).isEqualTo(10);
        assertSeatStates(oldSeatIds, "AVAILABLE", null, true);
        assertSeatStates(newSeatIds, "SOLD", orderId, false);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT old_flight_id, new_flight_id, old_seat_id, new_seat_id, price_diff, change_fee, status " +
                        "FROM change_record WHERE order_id = ? ORDER BY id", orderId);
        assertThat(records).hasSize(2);
        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            assertThat(((Number) record.get("old_flight_id")).longValue()).isEqualTo(oldFlightId);
            assertThat(((Number) record.get("new_flight_id")).longValue()).isEqualTo(newFlightId);
            assertThat(((Number) record.get("old_seat_id")).longValue()).isEqualTo(oldSeatIds.get(i));
            assertThat(((Number) record.get("new_seat_id")).longValue()).isEqualTo(newSeatIds.get(i));
            assertThat((BigDecimal) record.get("price_diff")).isEqualByComparingTo("200.00");
            assertThat((BigDecimal) record.get("change_fee")).isEqualByComparingTo("116.00");
            assertThat(record.get("status")).isEqualTo("SUCCESS");
        }
    }

    @Test
    void changeOrder_reversedSeatMappings_applyToRequestedPassengers() throws Exception {
        Long oldFlightId = createFlight("REVOLD", 1L, 3L, LocalDateTime.now(businessClock).plusDays(3),
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
        Long newFlightId = createFlight("REVNEW", 1L, 3L, LocalDateTime.now(businessClock).plusDays(5),
                new BigDecimal("700.00"), "ON_TIME", "PUBLISHED", 12);
        Long passengerTwo = createPassenger("Reversed Mapping Passenger");
        List<Long> oldSeatIds = getAvailableSeatIds(oldFlightId, 2);
        Long orderId = createAndPayOrder(oldFlightId, List.of(1L, passengerTwo), oldSeatIds);
        List<Long> newSeatIds = getAvailableSeatIds(newFlightId, 2);

        ChangeOrderDTO dto = changeDto(newFlightId, List.of(
                mapping(passengerTwo, newSeatIds.get(1)),
                mapping(1L, newSeatIds.get(0))));

        mockMvc.perform(post("/api/orders/" + orderId + "/change")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGED"));

        assertThat(passengerSeatId(orderId, 1L)).isEqualTo(newSeatIds.get(0));
        assertThat(passengerSeatId(orderId, passengerTwo)).isEqualTo(newSeatIds.get(1));

        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT old_seat_id, new_seat_id FROM change_record WHERE order_id = ?", orderId);
        assertThat(records).hasSize(2);
        assertThat(newSeatIdForOldSeat(records, oldSeatIds.get(0))).isEqualTo(newSeatIds.get(0));
        assertThat(newSeatIdForOldSeat(records, oldSeatIds.get(1))).isEqualTo(newSeatIds.get(1));
    }

    @Test
    void changeEndpoints_rejectOwnershipAnonymousAdminAndNonIssuedOrder() throws Exception {
        Long oldFlightId = createFlight("OWNOLD", 1L, 3L, LocalDateTime.now(businessClock).plusDays(3),
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
        Long newFlightId = createFlight("OWNNEW", 1L, 3L, LocalDateTime.now(businessClock).plusDays(4),
                new BigDecimal("520.00"), "ON_TIME", "PUBLISHED", 12);
        Long orderId = createAndPayOrder(oldFlightId, List.of(1L), getAvailableSeatIds(oldFlightId, 1));
        Long newSeatId = getAvailableSeatIds(newFlightId, 1).get(0);
        ChangeOrderDTO dto = changeDto(newFlightId, List.of(mapping(1L, newSeatId)));

        mockMvc.perform(get("/api/orders/" + orderId + "/change-options"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/" + orderId + "/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/orders/" + orderId + "/change-options")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/orders/" + orderId + "/change")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        String otherUserToken = createAndLoginOtherUser();
        mockMvc.perform(get("/api/orders/" + orderId + "/change-options")
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + orderId + "/change")
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());

        Long pendingOrderId = createOrder(oldFlightId, List.of(1L), getAvailableSeatIds(oldFlightId, 1));
        mockMvc.perform(post("/api/orders/" + pendingOrderId + "/change")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void changeOrder_rejectsInvalidFlightSeatAndPassengerRequests() throws Exception {
        assertRejectedChange(invalidCase("UNAV", new BigDecimal("500.00"), 1L, 3L,
                dto -> jdbcTemplate.update("UPDATE flight_seat SET status = 'SOLD', locked_by_order_id = ? WHERE id = ?",
                        dto.orderId(), dto.newSeatIds().get(0)),
                dto -> changeDto(dto.newFlightId(), List.of(mapping(dto.passengerIds().get(0), dto.newSeatIds().get(0)))),
                30002));

        assertRejectedChange(invalidCase("UNSELL", new BigDecimal("500.00"), 1L, 3L,
                dto -> jdbcTemplate.update("UPDATE flight SET status = 'CANCELLED' WHERE id = ?", dto.newFlightId()),
                dto -> changeDto(dto.newFlightId(), List.of(mapping(dto.passengerIds().get(0), dto.newSeatIds().get(0)))),
                30001));

        assertRejectedChange(invalidCase("CURR", new BigDecimal("500.00"), 1L, 3L,
                dto -> { },
                dto -> changeDto(dto.oldFlightId(), List.of(mapping(dto.passengerIds().get(0), dto.oldSeatIds().get(0)))),
                40001));

        assertRejectedChange(invalidCase("ROUTE", new BigDecimal("500.00"), 1L, 4L,
                dto -> { },
                dto -> changeDto(dto.newFlightId(), List.of(mapping(dto.passengerIds().get(0), dto.newSeatIds().get(0)))),
                40001));

        assertRejectedChange(invalidCase("MISSING", new BigDecimal("500.00"), 1L, 3L,
                dto -> { },
                dto -> changeDto(dto.newFlightId(), List.of()),
                10003));

        assertRejectedChange(invalidCase("DUPPASS", new BigDecimal("500.00"), 1L, 3L,
                dto -> { },
                dto -> changeDto(dto.newFlightId(), List.of(
                        mapping(dto.passengerIds().get(0), dto.newSeatIds().get(0)),
                        mapping(dto.passengerIds().get(0), dto.newSeatIds().get(1)))),
                10003));

        assertRejectedChange(invalidCase("DUPSEAT", new BigDecimal("500.00"), 1L, 3L,
                dto -> { },
                dto -> changeDto(dto.newFlightId(), List.of(
                        mapping(dto.passengerIds().get(0), dto.newSeatIds().get(0)),
                        mapping(dto.passengerIds().get(1), dto.newSeatIds().get(0)))),
                40006));
    }

    @Test
    void failedChange_doesNotMutateOrderInventorySeatsOrRecords() throws Exception {
        ChangeFixture fixture = createFixture("ROLLBACK", 2, 1L, 3L, new BigDecimal("500.00"));
        jdbcTemplate.update("UPDATE flight_seat SET status = 'SOLD', locked_by_order_id = ? WHERE id = ?",
                fixture.orderId(), fixture.newSeatIds().get(0));
        ChangeSnapshot before = snapshot(fixture.orderId(), fixture.oldFlightId(), fixture.newFlightId(),
                fixture.oldSeatIds(), fixture.newSeatIds());

        ChangeOrderDTO dto = changeDto(fixture.newFlightId(), List.of(
                mapping(fixture.passengerIds().get(0), fixture.newSeatIds().get(0)),
                mapping(fixture.passengerIds().get(1), fixture.newSeatIds().get(1))));

        mockMvc.perform(post("/api/orders/" + fixture.orderId() + "/change")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30002));

        assertThat(snapshot(fixture.orderId(), fixture.oldFlightId(), fixture.newFlightId(),
                fixture.oldSeatIds(), fixture.newSeatIds())).isEqualTo(before);
    }

    @Test
    void changeOptions_excludeFlightsEarlierThanOriginalPlus2Hours() throws Exception {
        // 原航班 now+3天;改签候选需 departure_time >= 原航班出发时间 + 2h
        LocalDateTime originalDep = LocalDateTime.now(businessClock).plusDays(3);
        Long oldFlightId = createFlight("OPTOLD", 1L, 3L, originalDep,
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
        // 候选A: 原航班+1h,晚于 NOW() 但早于原+2h,应被排除
        Long earlyFlightId = createFlight("OPTA", 1L, 3L, originalDep.plusHours(1),
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
        // 候选B: 原航班+3h,合法
        Long legalFlightId = createFlight("OPTB", 1L, 3L, originalDep.plusHours(3),
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
        // 候选C: 原航班+1天,合法
        Long laterFlightId = createFlight("OPTC", 1L, 3L, originalDep.plusDays(1),
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);

        List<Long> oldSeatIds = getAvailableSeatIds(oldFlightId, 1);
        Long orderId = createAndPayOrder(oldFlightId, List.of(1L), oldSeatIds);

        MvcResult result = mockMvc.perform(get("/api/orders/" + orderId + "/change-options")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Set<Long> candidateIds = new HashSet<>();
        for (JsonNode r : records) {
            candidateIds.add(r.path("flightId").asLong());
        }
        assertThat(candidateIds).doesNotContain(earlyFlightId);
        assertThat(candidateIds).contains(legalFlightId, laterFlightId);
    }

    @Test
    void changeOrder_rejectsNewFlightEarlierThanOriginalPlus2Hours() throws Exception {
        // 原航班 now+3天;新航班原+1h(晚于 NOW 但早于原+2h),应被 CHANGE_FLIGHT_EARLIER_THAN_ORIGINAL(50007) 拒绝
        LocalDateTime originalDep = LocalDateTime.now(businessClock).plusDays(3);
        Long oldFlightId = createFlight("REJOLD", 1L, 3L, originalDep,
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
        Long newFlightId = createFlight("REJNEW", 1L, 3L, originalDep.plusHours(1),
                new BigDecimal("600.00"), "ON_TIME", "PUBLISHED", 12);
        List<Long> oldSeatIds = getAvailableSeatIds(oldFlightId, 1);
        Long orderId = createAndPayOrder(oldFlightId, List.of(1L), oldSeatIds);
        List<Long> newSeatIds = getAvailableSeatIds(newFlightId, 1);

        ChangeSnapshot before = snapshot(orderId, oldFlightId, newFlightId, oldSeatIds, newSeatIds);

        ChangeOrderDTO dto = changeDto(newFlightId, List.of(mapping(1L, newSeatIds.get(0))));

        mockMvc.perform(post("/api/orders/" + orderId + "/change")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50007));

        assertThat(snapshot(orderId, oldFlightId, newFlightId, oldSeatIds, newSeatIds)).isEqualTo(before);
    }

    @Test
    void changeOrder_rejectsDepartedNewFlight_underUtcJvm() throws Exception {
        // issue #139 回归：JVM=UTC 容器下，改签到刚起飞的新航班仍应被拒（FLIGHT_NOT_SELLABLE = 30001）
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            // 原航班：业务时间 3 天后起飞（处于改签窗口内）
            Long oldFlightId = createFlight("TZOLD", 1L, 3L,
                    LocalDateTime.now(businessClock).plusDays(3),
                    new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
            // 新航班：业务时间 1 小时前已起飞（同航线）。8h 时区偏差会让裸 now() 误判为可改签。
            Long departedNewId = createFlight("TZNEW", 1L, 3L,
                    LocalDateTime.now(businessClock).minusHours(1),
                    new BigDecimal("600.00"), "ON_TIME", "PUBLISHED", 12);
            List<Long> oldSeatIds = getAvailableSeatIds(oldFlightId, 1);
            Long orderId = createAndPayOrder(oldFlightId, List.of(1L), oldSeatIds);
            List<Long> newSeatIds = getAvailableSeatIds(departedNewId, 1);
            ChangeSnapshot before = snapshot(orderId, oldFlightId, departedNewId, oldSeatIds, newSeatIds);

            ChangeOrderDTO dto = changeDto(departedNewId, List.of(mapping(1L, newSeatIds.get(0))));

            mockMvc.perform(post("/api/orders/" + orderId + "/change")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(30001));

            assertThat(snapshot(orderId, oldFlightId, departedNewId, oldSeatIds, newSeatIds)).isEqualTo(before);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void concurrentChangeRequests_sameNewSeat_onlyOneWins() throws Exception {
        Long oldFlightId = createFlight("CONOLD", 1L, 3L, LocalDateTime.now(businessClock).plusDays(3),
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
        Long newFlightId = createFlight("CONNEW", 1L, 3L, LocalDateTime.now(businessClock).plusDays(5),
                new BigDecimal("600.00"), "ON_TIME", "PUBLISHED", 12);
        Long passengerTwo = createPassenger("Concurrent Change Passenger");
        List<Long> oldSeats = getAvailableSeatIds(oldFlightId, 2);
        Long firstOrderId = createAndPayOrder(oldFlightId, List.of(1L), List.of(oldSeats.get(0)));
        Long secondOrderId = createAndPayOrder(oldFlightId, List.of(passengerTwo), List.of(oldSeats.get(1)));
        Long contestedNewSeat = getAvailableSeatIds(newFlightId, 1).get(0);

        String firstBody = objectMapper.writeValueAsString(
                changeDto(newFlightId, List.of(mapping(1L, contestedNewSeat))));
        String secondBody = objectMapper.writeValueAsString(
                changeDto(newFlightId, List.of(mapping(passengerTwo, contestedNewSeat))));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();
        AtomicReference<Exception> firstError = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> performConcurrentChange(firstOrderId, firstBody, startLatch, doneLatch, successCount, firstError));
        executor.submit(() -> performConcurrentChange(secondOrderId, secondBody, startLatch, doneLatch, successCount, firstError));

        startLatch.countDown();
        assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();
        if (firstError.get() != null) {
            fail("Unexpected concurrent change error", firstError.get());
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(changeRecordCount(firstOrderId) + changeRecordCount(secondOrderId)).isEqualTo(1);
        assertThat(seatState(contestedNewSeat).status()).isEqualTo("SOLD");
        assertThat(seatState(contestedNewSeat).lockedByOrderId()).isIn(firstOrderId, secondOrderId);
        assertThat(Set.of(orderStatus(firstOrderId), orderStatus(secondOrderId)))
                .containsExactlyInAnyOrder("ISSUED", "CHANGED");
    }

    private void performConcurrentChange(Long orderId, String body, CountDownLatch startLatch, CountDownLatch doneLatch,
                                         AtomicInteger successCount, AtomicReference<Exception> firstError) {
        try {
            startLatch.await();
            MvcResult result = mockMvc.perform(post("/api/orders/" + orderId + "/change")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            if (result.getResponse().getStatus() == 200
                    && "CHANGED".equals(response.path("data").path("status").asText())) {
                successCount.incrementAndGet();
            }
        } catch (Exception e) {
            firstError.compareAndSet(null, e);
        } finally {
            doneLatch.countDown();
        }
    }

    private InvalidCase invalidCase(String label, BigDecimal newPrice, Long newDepartureAirportId, Long newArrivalAirportId,
                                    FixtureMutator mutator, DtoFactory dtoFactory, int expectedCode) throws Exception {
        int passengerCount = label.equals("DUPPASS") || label.equals("DUPSEAT") ? 2 : 1;
        ChangeFixture fixture = createFixture(label, passengerCount, newDepartureAirportId, newArrivalAirportId, newPrice);
        mutator.mutate(fixture);
        return new InvalidCase(fixture, dtoFactory.create(fixture), expectedCode);
    }

    private void assertRejectedChange(InvalidCase invalidCase) throws Exception {
        ChangeFixture fixture = invalidCase.fixture();
        ChangeSnapshot before = snapshot(fixture.orderId(), fixture.oldFlightId(), fixture.newFlightId(),
                fixture.oldSeatIds(), fixture.newSeatIds());

        mockMvc.perform(post("/api/orders/" + fixture.orderId() + "/change")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCase.dto())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(invalidCase.expectedCode()));

        assertThat(snapshot(fixture.orderId(), fixture.oldFlightId(), fixture.newFlightId(),
                fixture.oldSeatIds(), fixture.newSeatIds())).isEqualTo(before);
    }

    private ChangeFixture createFixture(String label, int passengerCount, Long newDepartureAirportId,
                                        Long newArrivalAirportId, BigDecimal newPrice) throws Exception {
        Long oldFlightId = createFlight(label + "OLD", 1L, 3L, LocalDateTime.now(businessClock).plusDays(3),
                new BigDecimal("500.00"), "ON_TIME", "PUBLISHED", 12);
        Long newFlightId = createFlight(label + "NEW", newDepartureAirportId, newArrivalAirportId,
                LocalDateTime.now(businessClock).plusDays(5), newPrice, "ON_TIME", "PUBLISHED", 12);
        List<Long> passengerIds = new ArrayList<>();
        passengerIds.add(1L);
        for (int i = 1; i < passengerCount; i++) {
            passengerIds.add(createPassenger(label + " Passenger " + i));
        }
        List<Long> oldSeatIds = getAvailableSeatIds(oldFlightId, passengerCount);
        Long orderId = createAndPayOrder(oldFlightId, passengerIds, oldSeatIds);
        List<Long> newSeatIds = getAvailableSeatIds(newFlightId, Math.max(passengerCount, 2));
        return new ChangeFixture(orderId, oldFlightId, newFlightId, passengerIds, oldSeatIds, newSeatIds);
    }

    private Long createFlight(String label, Long departureAirportId, Long arrivalAirportId, LocalDateTime departure,
                              BigDecimal basePrice, String status, String publishStatus, int totalSeats) throws Exception {
        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo("CH" + COUNTER.incrementAndGet() + label);
        dto.setAirlineId(1L);
        dto.setDepartureAirportId(departureAirportId);
        dto.setArrivalAirportId(arrivalAirportId);
        dto.setDepartureTime(departure);
        dto.setArrivalTime(departure.plusHours(2));
        dto.setDurationMinutes(120);
        dto.setBasePrice(basePrice);
        dto.setTotalSeats(totalSeats);
        dto.setStatus(status);
        dto.setPublishStatus(publishStatus);

        MvcResult result = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return flightId;
    }

    private Long createOrder(Long flightId, List<Long> passengerIds, List<Long> seatIds) throws Exception {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        List<CreateOrderDTO.OrderItemDTO> items = new ArrayList<>();
        for (int i = 0; i < passengerIds.size(); i++) {
            CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
            item.setPassengerId(passengerIds.get(i));
            item.setSeatId(seatIds.get(i));
            items.add(item);
        }
        dto.setItems(items);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private Long createAndPayOrder(Long flightId, List<Long> passengerIds, List<Long> seatIds) throws Exception {
        Long orderId = createOrder(flightId, passengerIds, seatIds);
        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"));
        return orderId;
    }

    private Long createPassenger(String name) throws Exception {
        PassengerDTO dto = new PassengerDTO();
        long value = Math.floorMod(System.currentTimeMillis() + COUNTER.incrementAndGet(), 1_000_000_000_000L);
        dto.setName(name);
        dto.setIdCardNo("310101" + String.format("%012d", value));
        dto.setPassengerType("ADULT");
        dto.setPhone("139" + String.format("%08d", Math.floorMod(value, 100_000_000L)));

        MvcResult result = mockMvc.perform(post("/api/passengers")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private List<Long> getAvailableSeatIds(Long flightId, int count) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/flights/" + flightId + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seats = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        List<Long> ids = new ArrayList<>();
        for (JsonNode seat : seats) {
            if ("AVAILABLE".equals(seat.path("status").asText())) {
                ids.add(seat.path("id").asLong());
                if (ids.size() == count) {
                    return ids;
                }
            }
        }
        throw new IllegalStateException("No enough available seats for flight " + flightId);
    }

    private ChangeOrderDTO changeDto(Long newFlightId, List<ChangeOrderDTO.SeatMapping> mappings) {
        ChangeOrderDTO dto = new ChangeOrderDTO();
        dto.setNewFlightId(newFlightId);
        dto.setSeatMappings(mappings);
        return dto;
    }

    private ChangeOrderDTO.SeatMapping mapping(Long passengerId, Long newSeatId) {
        ChangeOrderDTO.SeatMapping mapping = new ChangeOrderDTO.SeatMapping();
        mapping.setPassengerId(passengerId);
        mapping.setNewSeatId(newSeatId);
        return mapping;
    }

    private String loginAsUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"SkyBooker@Init2026!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String createAndLoginOtherUser() throws Exception {
        String email = "change-other-" + System.currentTimeMillis() + "-" + COUNTER.incrementAndGet() + "@example.com";
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'user1@example.com'", String.class);
        jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, nickname, role, status, email_verified) " +
                        "VALUES(?, ?, ?, 'USER', 'NORMAL', 1)",
                email, hash, "Other Change User");
        return loginAsUser(email, "User@123456");
    }

    private ChangeSnapshot snapshot(Long orderId, Long oldFlightId, Long newFlightId,
                                    List<Long> oldSeatIds, List<Long> newSeatIds) {
        return new ChangeSnapshot(
                orderStatus(orderId),
                orderFlightId(orderId),
                passengerSeatIds(orderId),
                remainingSeats(oldFlightId),
                remainingSeats(newFlightId),
                seatStates(oldSeatIds),
                seatStates(newSeatIds),
                changeRecordCount(orderId));
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM ticket_order WHERE id = ?", String.class, orderId);
    }

    private Long orderFlightId(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT flight_id FROM ticket_order WHERE id = ?", Long.class, orderId);
    }

    private Integer remainingSeats(Long flightId) {
        return jdbcTemplate.queryForObject("SELECT remaining_seats FROM flight WHERE id = ?", Integer.class, flightId);
    }

    private Integer changeRecordCount(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM change_record WHERE order_id = ?",
                Integer.class, orderId);
    }

    private List<Long> passengerSeatIds(Long orderId) {
        return jdbcTemplate.queryForList(
                "SELECT seat_id FROM order_passenger WHERE order_id = ? ORDER BY id",
                Long.class, orderId);
    }

    private Long passengerSeatId(Long orderId, Long passengerId) {
        return jdbcTemplate.queryForObject(
                "SELECT seat_id FROM order_passenger WHERE order_id = ? AND passenger_id = ?",
                Long.class, orderId, passengerId);
    }

    private Long newSeatIdForOldSeat(List<Map<String, Object>> records, Long oldSeatId) {
        return records.stream()
                .filter(record -> oldSeatId.equals(((Number) record.get("old_seat_id")).longValue()))
                .map(record -> ((Number) record.get("new_seat_id")).longValue())
                .findFirst()
                .orElseThrow();
    }

    private List<SeatState> seatStates(List<Long> seatIds) {
        return seatIds.stream().map(this::seatState).toList();
    }

    private SeatState seatState(Long seatId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, locked_by_order_id, lock_expire_time FROM flight_seat WHERE id = ?", seatId);
        Object lockExpireTime = row.get("lock_expire_time");
        return new SeatState(
                (String) row.get("status"),
                row.get("locked_by_order_id") == null ? null : ((Number) row.get("locked_by_order_id")).longValue(),
                toLocalDateTime(lockExpireTime));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }

    private void assertSeatStates(List<Long> seatIds, String status, Long lockedByOrderId, boolean expectLockCleared) {
        for (Long seatId : seatIds) {
            SeatState seat = seatState(seatId);
            assertThat(seat.status()).isEqualTo(status);
            assertThat(seat.lockedByOrderId()).isEqualTo(lockedByOrderId);
            if (expectLockCleared) {
                assertThat(seat.lockExpireTime()).isNull();
            }
        }
    }

    private record ChangeFixture(Long orderId, Long oldFlightId, Long newFlightId, List<Long> passengerIds,
                                 List<Long> oldSeatIds, List<Long> newSeatIds) {
    }

    private record ChangeSnapshot(String orderStatus, Long orderFlightId, List<Long> passengerSeatIds,
                                  Integer oldRemainingSeats, Integer newRemainingSeats,
                                  List<SeatState> oldSeatStates, List<SeatState> newSeatStates,
                                  Integer changeRecordCount) {
    }

    private record SeatState(String status, Long lockedByOrderId, LocalDateTime lockExpireTime) {
    }

    private record InvalidCase(ChangeFixture fixture, ChangeOrderDTO dto, int expectedCode) {
    }

    private interface FixtureMutator {
        void mutate(ChangeFixture fixture);
    }

    private interface DtoFactory {
        ChangeOrderDTO create(ChangeFixture fixture);
    }
}
