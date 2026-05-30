package com.skybooker.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.auth.dto.UserLoginDTO;
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

class UserLoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void userLogin_success() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setEmail("user1@example.com");
        dto.setPassword("User@123456");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
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
        assertEquals("USER", ((Map<String, Object>) data.get("user")).get("role"));
    }

    @Test
    void userLogin_rejectsAdminAccount() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setEmail("admin@skybooker.local");
        dto.setPassword("Admin@123456");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        assertNotEquals(200, response.getCode());
    }

    @Test
    void userLogin_rejectsWrongPassword() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setEmail("user1@example.com");
        dto.setPassword("wrongpassword");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        assertNotEquals(200, response.getCode());
    }
}
