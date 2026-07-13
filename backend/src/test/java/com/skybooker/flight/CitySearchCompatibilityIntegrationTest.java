package com.skybooker.flight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.service.FlightRecommendationService;
import com.skybooker.common.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CitySearchCompatibilityIntegrationTest extends AbstractIntegrationTest {

    private static final long FIRST_CONNECTING_FLIGHT_ID = 98_000_001L;
    private static final long SECOND_CONNECTING_FLIGHT_ID = 98_000_002L;
    private static final long CONNECTING_ITINERARY_ID = 98_000_003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FlightRecommendationService recommendationService;

    private LocalDate departureDate;

    @BeforeEach
    void setUpFormalCityNamesAndFlights() {
        departureDate = LocalDate.now().plusDays(1);
        jdbcTemplate.update("UPDATE airport SET city='上海市' WHERE code IN ('SHA','PVG')");
        jdbcTemplate.update("UPDATE airport SET city='北京市' WHERE code IN ('PEK','PKX')");
        jdbcTemplate.update("UPDATE airport SET city='广州市' WHERE code='CAN'");
        jdbcTemplate.update(
                "UPDATE flight SET departure_time=TIMESTAMP(?, TIME(departure_time)), "
                        + "arrival_time=TIMESTAMP(?, TIME(arrival_time)), remaining_seats=20 WHERE id=1",
                departureDate, departureDate);
        jdbcTemplate.update("""
                INSERT INTO flight(
                    id, flight_no, airline_id, departure_airport_id, arrival_airport_id,
                    departure_time, arrival_time, duration_minutes, base_price,
                    remaining_seats, total_seats, status, publish_status, direct_flag
                ) VALUES
                (?, 'CITY1001', 1, 1, 5, ?, ?, 120, 500, 10, 10, 'ON_TIME', 'PUBLISHED', 1),
                (?, 'CITY1002', 1, 5, 3, ?, ?, 120, 500, 10, 10, 'ON_TIME', 'PUBLISHED', 1)
                """,
                FIRST_CONNECTING_FLIGHT_ID,
                departureDate.atTime(8, 0), departureDate.atTime(10, 0),
                SECOND_CONNECTING_FLIGHT_ID,
                departureDate.atTime(12, 0), departureDate.atTime(14, 0));
        jdbcTemplate.update("""
                INSERT INTO connecting_itinerary(id, first_flight_id, second_flight_id, publish_status)
                VALUES (?, ?, ?, 'PUBLISHED')
                """, CONNECTING_ITINERARY_ID, FIRST_CONNECTING_FLIGHT_ID, SECOND_CONNECTING_FLIGHT_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM connecting_itinerary WHERE id=?", CONNECTING_ITINERARY_ID);
        jdbcTemplate.update("DELETE FROM flight WHERE id IN (?, ?)",
                FIRST_CONNECTING_FLIGHT_ID, SECOND_CONNECTING_FLIGHT_ID);
        jdbcTemplate.update("UPDATE airport SET city='上海' WHERE code IN ('SHA','PVG')");
        jdbcTemplate.update("UPDATE airport SET city='北京' WHERE code IN ('PEK','PKX')");
        jdbcTemplate.update("UPDATE airport SET city='广州' WHERE code='CAN'");
    }

    @Test
    void directSearchShortAndFormalNamesReturnSameFlights() throws Exception {
        JsonNode shortResult = searchFlights(" 上海 ", "北京");
        JsonNode formalResult = searchFlights("上海市", "北京市");

        assertThat(shortResult.path("total").asLong()).isPositive();
        assertThat(shortResult.path("total").asLong()).isEqualTo(formalResult.path("total").asLong());
        assertThat(shortResult.path("records")).isEqualTo(formalResult.path("records"));
    }

    @Test
    void advancedSearchShortNamesMatchFormalDatabaseCities() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureCity", "上海")
                        .param("arrivalCity", "北京")
                        .param("departureDate", departureDate.toString())
                        .param("passengerCount", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void shortNamesFindConnectingItineraryAndFareCalendar() throws Exception {
        mockMvc.perform(get("/api/itineraries/search")
                        .param("departureCity", "上海")
                        .param("arrivalCity", "北京")
                        .param("departureDate", departureDate.toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.journeyType == 'CONNECTING')]").isNotEmpty());

        mockMvc.perform(get("/api/itineraries/fare-calendar")
                        .param("departureCity", "上海")
                        .param("arrivalCity", "北京")
                        .param("startDate", departureDate.toString())
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    void recommendationUsesSameCityNormalizationAndUnknownCityDoesNotFuzzyMatch() throws Exception {
        ParsedCondition condition = ParsedCondition.builder()
                .departureCity("上海")
                .arrivalCity("北京")
                .departureDate(departureDate)
                .build();
        assertThat(recommendationService.recommend(condition, null)).isNotEmpty();

        mockMvc.perform(get("/api/flights")
                        .param("departureCity", "不存在的上海")
                        .param("arrivalCity", "北京")
                        .param("departureDate", departureDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    private JsonNode searchFlights(String departureCity, String arrivalCity) throws Exception {
        String response = mockMvc.perform(get("/api/flights")
                        .param("departureCity", departureCity)
                        .param("arrivalCity", arrivalCity)
                        .param("departureDate", departureDate.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }
}
