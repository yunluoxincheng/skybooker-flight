package com.skybooker.flight;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlightCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
}
