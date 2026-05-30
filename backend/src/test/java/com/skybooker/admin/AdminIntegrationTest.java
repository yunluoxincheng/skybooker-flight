package com.skybooker.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.FlightFormDTO;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAsAdmin();
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

    private String loginAsUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user1@example.com\",\"password\":\"User@123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private FlightFormDTO buildValidFlightForm() {
        FlightFormDTO dto = new FlightFormDTO();
        dto.setFlightNo("TEST99");
        dto.setAirlineId(1L);
        dto.setDepartureAirportId(1L);
        dto.setArrivalAirportId(3L);
        dto.setDepartureTime(LocalDateTime.now().plusDays(3));
        dto.setArrivalTime(LocalDateTime.now().plusDays(3).plusHours(2));
        dto.setDurationMinutes(120);
        dto.setBasePrice(new BigDecimal("500.00"));
        dto.setTotalSeats(12);
        dto.setStatus("ON_TIME");
        dto.setPublishStatus("DRAFT");
        dto.setDirectFlag(true);
        dto.setBaggageAllowance("20kg");
        return dto;
    }

    // ---- Flight Management ----

    @Test
    void listFlights_returnsAllStatuses() throws Exception {
        mockMvc.perform(get("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(5)));
    }

    @Test
    void createFlight_success() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.flightNo").value("TEST99"))
                .andExpect(jsonPath("$.data.publishStatus").value("DRAFT"));
    }

    @Test
    void createFlight_rejectsSameAirport() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setArrivalAirportId(dto.getDepartureAirportId());

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsArrivalBeforeDeparture() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setArrivalTime(dto.getDepartureTime().minusHours(1));

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsInvalidAirline() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setAirlineId(99999L);

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsInvalidAirport() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setDepartureAirportId(99999L);

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsInvalidStatus() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setStatus("INVALID_STATUS");

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void createFlight_rejectsInvalidPublishStatus() throws Exception {
        FlightFormDTO dto = buildValidFlightForm();
        dto.setPublishStatus("LIVE");

        mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void updateFlight_success() throws Exception {
        FlightFormDTO createDto = buildValidFlightForm();
        MvcResult createResult = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        FlightFormDTO updateDto = buildValidFlightForm();
        updateDto.setFlightNo("TEST88");
        updateDto.setBasePrice(new BigDecimal("600.00"));

        mockMvc.perform(put("/api/admin/flights/" + flightId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.flightNo").value("TEST88"))
                .andExpect(jsonPath("$.data.basePrice").value(600.00));
    }

    @Test
    void publishAndUnpublishFlight() throws Exception {
        FlightFormDTO createDto = buildValidFlightForm();
        MvcResult createResult = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/unpublish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void generateSeats_success() throws Exception {
        FlightFormDTO createDto = buildValidFlightForm();
        createDto.setTotalSeats(6);
        createDto.setPublishStatus("PUBLISHED");
        MvcResult createResult = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Verify seats created via public endpoint (flight is PUBLISHED)
        mockMvc.perform(get("/api/flights/" + flightId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    void generateSeats_rejectsDuplicateGeneration() throws Exception {
        FlightFormDTO createDto = buildValidFlightForm();
        createDto.setTotalSeats(6);
        MvcResult createResult = mockMvc.perform(post("/api/admin/flights")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long flightId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/flights/" + flightId + "/generate-seats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003));
    }

    // ---- Order Management ----

    @Test
    void listOrders_success() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].userId").exists())
                .andExpect(jsonPath("$.data.records[0].userEmail").exists())
                .andExpect(jsonPath("$.data.records[0].userNickname").exists());
    }

    @Test
    void getOrderDetail_success() throws Exception {
        mockMvc.perform(get("/api/admin/orders/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("DEMO202605260001"))
                .andExpect(jsonPath("$.data.passengers").isArray())
                .andExpect(jsonPath("$.data.passengers.length()").value(1));
    }

    // ---- User Management ----

    @Test
    void listUsers_success() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void disableAndEnableUser() throws Exception {
        mockMvc.perform(post("/api/admin/users/2/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Disabled user login returns 403 (ACCOUNT_DISABLED)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user1@example.com\",\"password\":\"User@123456\"}"))
                .andExpect(status().isForbidden());

        // Re-enable
        mockMvc.perform(post("/api/admin/users/2/enable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Should be able to login again
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user1@example.com\",\"password\":\"User@123456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void disableUser_protectsAdminAccount() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));
    }

    @Test
    void enableUser_protectsAdminAccount() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/enable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));
    }

    // ---- Auth Isolation ----

    @Test
    void adminEndpoints_rejectUserToken() throws Exception {
        String userToken = loginAsUser();

        mockMvc.perform(get("/api/admin/flights")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_rejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/flights"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }
}
