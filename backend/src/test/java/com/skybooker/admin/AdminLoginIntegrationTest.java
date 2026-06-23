package com.skybooker.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.AdminLoginDTO;
import com.skybooker.common.AbstractIntegrationTest;
import com.skybooker.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminLoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminLogin_success() throws Exception {
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("SkyBooker@Init2026!");

        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        assertEquals(200, response.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertNotNull(data.get("accessToken"));
        assertEquals("Bearer", data.get("tokenType"));
        assertEquals("ADMIN", ((Map<String, Object>) data.get("admin")).get("role"));
    }

    @Test
    void adminLogin_rejectsUserAccount() throws Exception {
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("user1@example.com");
        dto.setPassword("User@123456");

        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        assertNotEquals(200, response.getCode());
    }

    @Test
    void adminLogin_rejectsWrongPassword() throws Exception {
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("wrongpassword");

        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        assertNotEquals(200, response.getCode());
    }
}
