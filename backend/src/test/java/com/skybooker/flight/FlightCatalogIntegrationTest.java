package com.skybooker.flight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlightCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tomorrowStr;

    @BeforeEach
    void refreshFlightDates() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        tomorrowStr = tomorrow;
        jdbcTemplate.update(
                "UPDATE flight SET departure_time = TIMESTAMP(?, TIME(departure_time)), " +
                        "arrival_time = TIMESTAMP(?, TIME(arrival_time)) WHERE id BETWEEN 2 AND 5",
                tomorrow, tomorrow);
    }

    @Test
    void searchFlights_byRouteAndDate() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureCity", "上海")
                        .param("arrivalCity", "北京")
                        .param("departureDate", "2026-05-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_byFlightNoAndDate() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("flightNo", "MU5101")
                        .param("departureDate", "2026-05-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_paginated() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", "2026-05-26")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    void searchFlights_byRouteAndDateRange() throws Exception {
        jdbcTemplate.update(
                "UPDATE flight SET departure_time = TIMESTAMP(?, TIME(departure_time)), " +
                        "arrival_time = TIMESTAMP(?, TIME(arrival_time)) WHERE id = 2",
                "2026-08-02", "2026-08-02");
        jdbcTemplate.update(
                "UPDATE flight SET departure_time = TIMESTAMP(?, TIME(departure_time)), " +
                        "arrival_time = TIMESTAMP(?, TIME(arrival_time)) WHERE id = 3",
                "2026-08-05", "2026-08-05");

        mockMvc.perform(get("/api/flights")
                        .param("departureCity", "上海")
                        .param("arrivalCity", "北京")
                        .param("departureDateStart", "2026-08-02")
                        .param("departureDateEnd", "2026-08-05")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].flightNo",
                        containsInAnyOrder("CZ3101", "CA1502")))
                .andExpect(jsonPath("$.data.records[*].departureTime",
                        everyItem(anyOf(startsWith("2026-08-02"), startsWith("2026-08-05")))));
    }

    @Test
    void getFlightDetail_success() throws Exception {
        mockMvc.perform(get("/api/flights/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.flightNo").value("MU5101"))
                .andExpect(jsonPath("$.data.airlineName").exists())
                .andExpect(jsonPath("$.data.departureCity").exists())
                .andExpect(jsonPath("$.data.arrivalCity").exists());
    }

    @Test
    void getFlightDetail_notFound() throws Exception {
        mockMvc.perform(get("/api/flights/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFlightSeats_success() throws Exception {
        mockMvc.perform(get("/api/flights/1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThan(0)));
    }

    @Test
    void getFlightSeats_scopedToFlight() throws Exception {
        String seats1 = mockMvc.perform(get("/api/flights/1/seats"))
                .andReturn().getResponse().getContentAsString();
        String seats2 = mockMvc.perform(get("/api/flights/2/seats"))
                .andReturn().getResponse().getContentAsString();
        assert !seats1.equals(seats2) : "Different flights should have different seats";
    }

    // --- Advanced filter tests ---

    @Test
    void searchFlights_filterByAirline() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("airlineId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_filterByPriceRange() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("minPrice", "500")
                        .param("maxPrice", "700"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[*].basePrice").value(everyItem(lessThanOrEqualTo(700.0))))
                .andExpect(jsonPath("$.data.records[*].basePrice").value(everyItem(greaterThanOrEqualTo(500.0))));
    }

    @Test
    void searchFlights_filterByDepartureTime() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("departureTimeStart", "06:00")
                        .param("departureTimeEnd", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThan(0)));
    }

    @Test
    void searchFlights_invalidTimeEnd24_returns400WithFriendlyMessage() throws Exception {
        // 24:00 不是合法 LocalTime,应返回友好校验错误而非暴露 Java 类型转换细节
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("departureTimeStart", "18:00")
                        .param("departureTimeEnd", "24:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003))
                .andExpect(jsonPath("$.message").value(containsString("HH:mm")))
                .andExpect(jsonPath("$.message").value(not(containsString("java.time"))));
    }

    @Test
    void searchFlights_invalidSort_returns400WithLegalEnums() throws Exception {
        // 非法 sort 不再静默回落默认排序,返回 400 并提示合法枚举
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("sort", "seats_asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003))
                .andExpect(jsonPath("$.message").value(containsString("排序参数")))
                .andExpect(jsonPath("$.message").value(containsString("SEATS_DESC")));
    }

    @Test
    void searchFlights_validSortSeatsDesc_returns200() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("sort", "SEATS_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_filterByMaxDuration() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("maxDurationMinutes", "140"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[*].durationMinutes").value(everyItem(lessThanOrEqualTo(140))));
    }

    @Test
    void searchFlights_filterByDirectOnly() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("directOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_filterByStatus() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("status", "ON_TIME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[*].status").value(everyItem(is("ON_TIME"))));
    }

    @Test
    void searchFlights_filterByPassengerCount() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("passengerCount", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_filterByCabinAvailability() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("passengerCount", "2")
                        .param("cabinClass", "ECONOMY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    // --- Sort tests ---

    @Test
    void searchFlights_sortByPriceAsc() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("size", "10")
                        .param("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThan(1)));
    }

    @Test
    void searchFlights_sortByPriceAscLowercase() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_sortByDurationAsc() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("sort", "DURATION_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_sortByTimeAsc() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("sort", "TIME_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_sortBySeatsDesc() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("sort", "seats_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_sortByPunctualityDesc() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr)
                        .param("sort", "PUNCTUAL_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void searchFlights_defaultSortUnchanged() throws Exception {
        mockMvc.perform(get("/api/flights")
                        .param("departureDate", tomorrowStr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }
}
