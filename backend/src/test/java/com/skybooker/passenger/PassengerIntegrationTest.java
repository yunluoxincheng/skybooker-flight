package com.skybooker.passenger;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PassengerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String adminToken;

    private static final AtomicInteger counter = new AtomicInteger(0);
    private String uniqueIdCardNo() {
        return "310101" + String.format("%012d", System.currentTimeMillis() % 1000000000000L + counter.incrementAndGet());
    }

    @BeforeEach
    void setUp() throws Exception {
        userToken = obtainUserToken();
        adminToken = obtainAdminToken();
    }

    private String obtainUserToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user1@example.com\",\"password\":\"User@123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("accessToken").asText();
    }

    private String obtainAdminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("accessToken").asText();
    }

    @Test
    void listMyPassengers_success() throws Exception {
        mockMvc.perform(get("/api/passengers")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createPassenger_success() throws Exception {
        PassengerDTO dto = new PassengerDTO();
        String idCardNo = uniqueIdCardNo();
        dto.setName("测试乘机人");
        dto.setIdCardNo(idCardNo);
        dto.setPassengerType("ADULT");
        dto.setPhone("13900001111");

        mockMvc.perform(post("/api/passengers")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("测试乘机人"))
                .andExpect(jsonPath("$.data.idCardNo").value(idCardNo));
    }

    @Test
    void createPassenger_rejectsDuplicateIdCard() throws Exception {
        String idCard = uniqueIdCardNo();

        PassengerDTO dto = new PassengerDTO();
        dto.setName("首次乘机人");
        dto.setIdCardNo(idCard);
        dto.setPassengerType("ADULT");
        dto.setPhone("13900009999");

        mockMvc.perform(post("/api/passengers")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        PassengerDTO dup = new PassengerDTO();
        dup.setName("重复乘机人");
        dup.setIdCardNo(idCard);
        dup.setPassengerType("ADULT");
        dup.setPhone("13900008888");

        mockMvc.perform(post("/api/passengers")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40005));
    }

    @Test
    void updatePassenger_success() throws Exception {
        PassengerDTO dto = new PassengerDTO();
        dto.setName("更新名称");
        dto.setIdCardNo("999999999999999999");

        mockMvc.perform(put("/api/passengers/1")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("更新名称"));
    }

    @Test
    void deletePassenger_success() throws Exception {
        // First create a passenger without order history
        PassengerDTO dto = new PassengerDTO();
        dto.setName("待删除乘机人");
        dto.setIdCardNo(uniqueIdCardNo());

        mockMvc.perform(post("/api/passengers")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        // Passenger id from create response
        String listBody = mockMvc.perform(get("/api/passengers")
                        .header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getContentAsString();
        var arr = objectMapper.readTree(listBody).get("data");
        Long newId = null;
        for (var item : arr) {
            if ("待删除乘机人".equals(item.get("name").asText())) {
                newId = item.get("id").asLong();
                break;
            }
        }

        if (newId != null) {
            mockMvc.perform(delete("/api/passengers/" + newId)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void deletePassenger_rejectsOrderHistory() throws Exception {
        // Passenger id=1 has order history from seed data
        mockMvc.perform(delete("/api/passengers/1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40004));
    }

    @Test
    void passengerEndpoints_rejectAdminToken() throws Exception {
        mockMvc.perform(get("/api/passengers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePassenger_rejectsOtherUsersPassenger() throws Exception {
        PassengerDTO dto = new PassengerDTO();
        dto.setName("非法修改");
        dto.setIdCardNo("777777777777777777");

        mockMvc.perform(put("/api/passengers/99999")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }
}
