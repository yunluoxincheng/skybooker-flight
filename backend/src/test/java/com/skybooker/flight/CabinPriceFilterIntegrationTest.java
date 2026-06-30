package com.skybooker.flight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightCabinDTO;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.service.FlightRecommendationService;
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
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * issue #55:搜索价格筛选/排序按舱位价。
 * 传入 cabinClass 时,minPrice/maxPrice 与 PRICE_ASC 应基于 flight_cabin.price,
 * 而非 flight.base_price。
 *
 * 用 flightNo 精确隔离新建航班,避免被其他测试类残留的航班数据干扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CabinPriceFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FlightRecommendationService recommendationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"SkyBooker@Init2026!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private FlightCabinDTO cabin(String cabinClass, String price, int totalSeats) {
        FlightCabinDTO d = new FlightCabinDTO();
        d.setCabinClass(cabinClass);
        d.setPrice(new BigDecimal(price));
        d.setTotalSeats(totalSeats);
        return d;
    }

    /** 创建上海→北京、PUBLISHED、含 BUSINESS 舱座位的多舱位航班。 */
    private void createMultiCabinFlight(String flightNo, int totalSeats, int businessSeats,
                                        String businessPrice, String economyPrice) throws Exception {
        cleanupFlight(flightNo); // 清理同 flightNo 残留,避免 @DirtiesContext 不清 DB 的累积干扰

        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo(flightNo);
        dto.setAirlineId(1L);
        dto.setDepartureAirportId(1L);
        dto.setArrivalAirportId(3L);
        dto.setDepartureTime(LocalDateTime.now().plusDays(7));
        dto.setArrivalTime(LocalDateTime.now().plusDays(7).plusHours(2));
        dto.setDurationMinutes(120);
        dto.setBasePrice(new BigDecimal(economyPrice));
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
        Long flightId = objectMapper.readTree(r.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(put("/api/admin/flights/" + flightId + "/cabins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                cabin("BUSINESS", businessPrice, businessSeats),
                                cabin("ECONOMY", economyPrice, totalSeats - businessSeats)))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    /** 清理指定 flightNo 的残留航班及其座位/舱位配置(测试间 DB 累积隔离)。 */
    private void cleanupFlight(String flightNo) {
        jdbcTemplate.update("DELETE fs FROM flight_seat fs JOIN flight f ON fs.flight_id = f.id WHERE f.flight_no = ?", flightNo);
        jdbcTemplate.update("DELETE fc FROM flight_cabin fc JOIN flight f ON fc.flight_id = f.id WHERE f.flight_no = ?", flightNo);
        jdbcTemplate.update("DELETE FROM flight WHERE flight_no = ?", flightNo);
    }

    @Test
    void search_cabinMaxPrice_includesFlightWithinBudget() throws Exception {
        createMultiCabinFlight("CPFIN", 6, 2, "1200.00", "500.00"); // BUSINESS 价 1200
        mockMvc.perform(get("/api/flights")
                        .param("flightNo", "CPFIN")
                        .param("cabinClass", "BUSINESS").param("maxPrice", "1500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].flightNo").value(hasItem("CPFIN")));
    }

    @Test
    void search_cabinMaxPrice_excludesFlightOverBudget() throws Exception {
        createMultiCabinFlight("CPFEX", 6, 2, "1200.00", "500.00"); // BUSINESS 价 1200
        mockMvc.perform(get("/api/flights")
                        .param("flightNo", "CPFEX")
                        .param("cabinClass", "BUSINESS").param("maxPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].flightNo").value(not(hasItem("CPFEX"))));
    }

    @Test
    void search_cabinMinPrice_filtersByCabinPrice() throws Exception {
        createMultiCabinFlight("CPFMIN", 6, 2, "1200.00", "500.00"); // BUSINESS 价 1200
        // minPrice=1000 → 1200 >= 1000 含
        mockMvc.perform(get("/api/flights")
                        .param("flightNo", "CPFMIN")
                        .param("cabinClass", "BUSINESS").param("minPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].flightNo").value(hasItem("CPFMIN")));
        // minPrice=1500 → 1200 < 1500 不含
        mockMvc.perform(get("/api/flights")
                        .param("flightNo", "CPFMIN")
                        .param("cabinClass", "BUSINESS").param("minPrice", "1500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].flightNo").value(not(hasItem("CPFMIN"))));
    }

    @Test
    void search_cabinPriceSort_ascByCabinPrice() throws Exception {
        createMultiCabinFlight("CPFHI", 6, 2, "1200.00", "500.00"); // BUSINESS 1200
        createMultiCabinFlight("CPFLO", 6, 2, "800.00", "400.00");  // BUSINESS 800

        String json = mockMvc.perform(get("/api/flights")
                        .param("departureCity", "上海").param("arrivalCity", "北京")
                        .param("cabinClass", "BUSINESS").param("sort", "PRICE_ASC")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode records = objectMapper.readTree(json).get("data").get("records");
        int idxLo = -1, idxHi = -1;
        for (int i = 0; i < records.size(); i++) {
            String no = records.get(i).get("flightNo").asText();
            if ("CPFLO".equals(no)) idxLo = i;
            if ("CPFHI".equals(no)) idxHi = i;
        }
        assertTrue(idxLo >= 0 && idxHi >= 0, "两个公务舱航班都应返回");
        assertTrue(idxLo < idxHi, "公务舱低价航班应排在高价航班之前");
    }

    @Test
    void search_rejectsInvalidCabinClass() throws Exception {
        mockMvc.perform(get("/api/flights").param("cabinClass", "LUXURY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void search_withoutCabin_fallsBackToBasePrice() throws Exception {
        // basePrice = economyPrice = 500;不传 cabin 时 maxPrice 按 base_price
        createMultiCabinFlight("CPFBASE", 6, 2, "1200.00", "500.00");
        mockMvc.perform(get("/api/flights")
                        .param("flightNo", "CPFBASE")
                        .param("maxPrice", "600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].flightNo").value(hasItem("CPFBASE")));
    }

    @Test
    void recommend_cabinPriceSort_ascByCabinPrice() throws Exception {
        // base_price 与 BUSINESS 价相反:RECHI base 400 < RECLO base 500,但 BUSINESS RECLO 850 < RECHI 880
        // 若排序仍按 base_price 会得 [RECHI,RECLO];按舱位价应为 [RECLO,RECHI]
        // 用独特价格区间 [830,900] 隔离,排除残留 CPFLO(800)/CPFHI(1200) 等航班
        createMultiCabinFlight("RECLO", 6, 2, "850.00", "500.00");
        createMultiCabinFlight("RECHI", 6, 2, "880.00", "400.00");

        ParsedCondition condition = ParsedCondition.builder()
                .departureCity("上海").arrivalCity("北京")
                .cabinClass("BUSINESS").sort("PRICE_ASC")
                .minPrice(new BigDecimal("830")).maxPrice(new BigDecimal("900"))
                .build();

        List<Map<String, Object>> cards = recommendationService.recommend(condition, null);

        int idxLo = -1, idxHi = -1;
        for (int i = 0; i < cards.size(); i++) {
            String no = String.valueOf(cards.get(i).get("flightNo"));
            if ("RECLO".equals(no)) idxLo = i;
            if ("RECHI".equals(no)) idxHi = i;
        }
        assertTrue(idxLo >= 0 && idxHi >= 0, "两个公务舱航班都应返回");
        assertTrue(idxLo < idxHi, "AI 推荐应按公务舱价升序(RECLO 850 < RECHI 880),而非 base_price");
    }
}
