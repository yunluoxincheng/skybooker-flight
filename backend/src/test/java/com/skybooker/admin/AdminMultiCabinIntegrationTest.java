package com.skybooker.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightCabinDTO;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 多舱位支持(issue #50 / B-CABIN-1)集成测试:舱位配置、按舱位生成座位、
 * 配置校验(求和/比例/重复/已生成守护)、无配置回退、FlightVO.cabins、跨舱位下单。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminMultiCabinIntegrationTest {

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
        adminToken = loginAsAdmin();
        userToken = loginAsUser();
    }

    private String loginAsAdmin() throws Exception {
        return login("/api/admin/auth/login",
                "{\"username\":\"admin\",\"password\":\"SkyBooker@Init2026!\"}");
    }

    private String loginAsUser() throws Exception {
        return login("/api/auth/login",
                "{\"email\":\"user1@example.com\",\"password\":\"User@123456\"}");
    }

    private String login(String url, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private Long createFlight(int totalSeats) throws Exception {
        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo("MC" + System.nanoTime() % 1000000);
        dto.setAirlineId(1L);
        dto.setDepartureAirportId(1L);
        dto.setArrivalAirportId(3L);
        dto.setDepartureTime(LocalDateTime.now().plusDays(7));
        dto.setArrivalTime(LocalDateTime.now().plusDays(7).plusHours(2));
        dto.setDurationMinutes(120);
        dto.setBasePrice(new BigDecimal("500.00"));
        dto.setTotalSeats(totalSeats);
        dto.setStatus("ON_TIME");
        dto.setPublishStatus("PUBLISHED");
        dto.setDirectFlag(true);

        MvcResult r = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private FlightCabinDTO cabin(String cabinClass, String price, int totalSeats) {
        FlightCabinDTO d = new FlightCabinDTO();
        d.setCabinClass(cabinClass);
        d.setPrice(new BigDecimal(price));
        d.setTotalSeats(totalSeats);
        return d;
    }

    private void setCabins(Long flightId, List<FlightCabinDTO> cabins) throws Exception {
        mockMvc.perform(put("/api/admin/flights/" + flightId + "/cabins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cabins)))
                .andExpect(status().isOk());
    }

    private void generateSeats(Long flightId) throws Exception {
        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private Integer countSeatsByCabin(Long flightId, String cabinClass) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flight_seat WHERE flight_id = ? AND cabin_class = ?",
                Integer.class, flightId, cabinClass);
    }

    private BigDecimal priceOfFirstSeatByCabin(Long flightId, String cabinClass) {
        return jdbcTemplate.queryForObject(
                "SELECT price FROM flight_seat WHERE flight_id = ? AND cabin_class = ? ORDER BY id LIMIT 1",
                BigDecimal.class, flightId, cabinClass);
    }

    private Long getAvailableSeatIdByCabin(Long flightId, String cabinClass) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/flights/" + flightId + "/seats"))
                .andExpect(status().isOk())
                .andReturn();
        var seats = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        for (var seat : seats) {
            if ("AVAILABLE".equals(seat.get("status").asText())
                    && cabinClass.equals(seat.get("cabinClass").asText())) {
                return seat.get("id").asLong();
            }
        }
        throw new IllegalStateException("No available " + cabinClass + " seat for flight " + flightId);
    }

    // ---- 多舱位配置 + 生成 ----

    @Test
    void setCabins_thenGenerate_producesCabinPartitionedSeatsWithCabinPricing() throws Exception {
        Long flightId = createFlight(18);
        setCabins(flightId, List.of(
                cabin("FIRST", "2000.00", 2),
                cabin("BUSINESS", "1200.00", 4),
                cabin("ECONOMY", "500.00", 12)));
        generateSeats(flightId);

        // 各舱座位数与配置 1:1 对齐
        org.junit.jupiter.api.Assertions.assertEquals(2, countSeatsByCabin(flightId, "FIRST"));
        org.junit.jupiter.api.Assertions.assertEquals(4, countSeatsByCabin(flightId, "BUSINESS"));
        org.junit.jupiter.api.Assertions.assertEquals(12, countSeatsByCabin(flightId, "ECONOMY"));

        // 价格按舱位
        org.junit.jupiter.api.Assertions.assertEquals(0, priceOfFirstSeatByCabin(flightId, "FIRST").compareTo(new BigDecimal("2000.00")));
        org.junit.jupiter.api.Assertions.assertEquals(0, priceOfFirstSeatByCabin(flightId, "BUSINESS").compareTo(new BigDecimal("1200.00")));
        org.junit.jupiter.api.Assertions.assertEquals(0, priceOfFirstSeatByCabin(flightId, "ECONOMY").compareTo(new BigDecimal("500.00")));

        // 公开座位接口总数 = 18
        mockMvc.perform(get("/api/flights/" + flightId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(18));
    }

    @Test
    void setCabins_rejectsUnrealisticRatioWhereEconomyLessThanBusiness() throws Exception {
        Long flightId = createFlight(18);
        // 经济舱 4 < 公务舱 8,违反现实布局
        mockMvc.perform(put("/api/admin/flights/" + flightId + "/cabins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                cabin("BUSINESS", "1200.00", 8),
                                cabin("ECONOMY", "500.00", 4)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void setCabins_rejectsSumMismatchingFlightTotalSeats() throws Exception {
        Long flightId = createFlight(18);
        // 2+4+12=18 ≠ totalSeats... 这里故意用 2+4+11=17 ≠ 18
        mockMvc.perform(put("/api/admin/flights/" + flightId + "/cabins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                cabin("FIRST", "2000.00", 2),
                                cabin("BUSINESS", "1200.00", 4),
                                cabin("ECONOMY", "500.00", 11)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void setCabins_rejectsWhenSeatsAlreadyGenerated() throws Exception {
        Long flightId = createFlight(6);
        generateSeats(flightId); // 回退生成单经济舱,产生库存

        mockMvc.perform(put("/api/admin/flights/" + flightId + "/cabins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                cabin("FIRST", "2000.00", 2),
                                cabin("ECONOMY", "500.00", 4)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40008)); // FLIGHT_HAS_INVENTORY
    }

    @Test
    void setCabins_rejectsDuplicateCabinClass() throws Exception {
        Long flightId = createFlight(12);
        mockMvc.perform(put("/api/admin/flights/" + flightId + "/cabins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                cabin("ECONOMY", "500.00", 6),
                                cabin("ECONOMY", "600.00", 6)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void getCabins_returnsConfiguredCabinsWithAvailability() throws Exception {
        Long flightId = createFlight(18);
        setCabins(flightId, List.of(
                cabin("FIRST", "2000.00", 2),
                cabin("BUSINESS", "1200.00", 4),
                cabin("ECONOMY", "500.00", 12)));
        generateSeats(flightId);

        mockMvc.perform(get("/api/admin/flights/" + flightId + "/cabins")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].cabinClass").value("FIRST"))
                .andExpect(jsonPath("$.data[0].totalSeats").value(2))
                .andExpect(jsonPath("$.data[0].availableSeats").value(2))
                .andExpect(jsonPath("$.data[1].cabinClass").value("BUSINESS"))
                .andExpect(jsonPath("$.data[2].cabinClass").value("ECONOMY"))
                .andExpect(jsonPath("$.data[2].availableSeats").value(12));
    }

    @Test
    void generateSeats_withoutConfig_fallsBackToSingleEconomy() throws Exception {
        Long flightId = createFlight(6);
        generateSeats(flightId); // 未配舱位,回退单经济舱

        mockMvc.perform(get("/api/admin/flights/" + flightId + "/cabins")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].cabinClass").value("ECONOMY"))
                .andExpect(jsonPath("$.data[0].totalSeats").value(6))
                .andExpect(jsonPath("$.data[0].price").value(500.00));
    }

    @Test
    void publicFlightDetail_includesCabinsField() throws Exception {
        Long flightId = createFlight(18);
        setCabins(flightId, List.of(
                cabin("FIRST", "2000.00", 2),
                cabin("BUSINESS", "1200.00", 4),
                cabin("ECONOMY", "500.00", 12)));

        // 用户端 booking 页通过公开端点读取航班详情(含各舱位价格/余座)
        mockMvc.perform(get("/api/flights/" + flightId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cabins").isArray())
                .andExpect(jsonPath("$.data.cabins.length()").value(3))
                .andExpect(jsonPath("$.data.cabins[0].cabinClass").value("FIRST"));
    }

    @Test
    void createOrder_buyBusinessCabin_pricedByCabin() throws Exception {
        Long flightId = createFlight(18);
        setCabins(flightId, List.of(
                cabin("FIRST", "2000.00", 2),
                cabin("BUSINESS", "1200.00", 4),
                cabin("ECONOMY", "500.00", 12)));
        generateSeats(flightId);

        Long businessSeatId = getAvailableSeatIdByCabin(flightId, "BUSINESS");

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
        item.setPassengerId(1L);
        item.setSeatId(businessSeatId);
        dto.setItems(List.of(item));

        // 下单公务舱座位:票价 = 公务舱价 1200(+ 机建 50 + 燃油 30)
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ticketAmount").value(1200.00))
                .andExpect(jsonPath("$.data.totalAmount").value(1280.00));
    }

    @Test
    void updateFlight_rejectsTotalSeatsChangeWhenCabinsConfigured() throws Exception {
        Long flightId = createFlight(18);
        setCabins(flightId, List.of(
                cabin("FIRST", "2000.00", 2),
                cabin("BUSINESS", "1200.00", 4),
                cabin("ECONOMY", "500.00", 12)));

        FlightFormDTO update = new FlightFormDTO();
        update.setFlightNo("MC" + System.nanoTime() % 1000000);
        update.setAirlineId(1L);
        update.setDepartureAirportId(1L);
        update.setArrivalAirportId(3L);
        update.setDepartureTime(LocalDateTime.now().plusDays(7));
        update.setArrivalTime(LocalDateTime.now().plusDays(7).plusHours(2));
        update.setDurationMinutes(120);
        update.setBasePrice(new BigDecimal("500.00"));
        update.setTotalSeats(20); // 配过舱位后改总数,应拒绝(防 cabin 总数与航班总数漂移)
        update.setStatus("ON_TIME");
        update.setPublishStatus("PUBLISHED");
        update.setDirectFlag(true);

        mockMvc.perform(put("/api/admin/flights/" + flightId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40008));
    }

    @Test
    void setCabins_rejectsInvalidCabinClass() throws Exception {
        Long flightId = createFlight(18);
        mockMvc.perform(put("/api/admin/flights/" + flightId + "/cabins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(cabin("LUXURY", "1000.00", 18)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void generateSeats_rejectsWhenCabinSumDriftsFromTotalSeats() throws Exception {
        Long flightId = createFlight(18);
        setCabins(flightId, List.of(
                cabin("FIRST", "2000.00", 2),
                cabin("BUSINESS", "1200.00", 4),
                cabin("ECONOMY", "500.00", 12)));
        // 模拟脏数据:直接改 flight.total_seats 破坏不变量(绕过 service 守护),验证 generateSeats 二次校验
        jdbcTemplate.update("UPDATE flight SET total_seats = 20 WHERE id = ?", flightId);

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }
}
