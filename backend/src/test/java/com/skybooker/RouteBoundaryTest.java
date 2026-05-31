package com.skybooker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.AdminLoginDTO;
import com.skybooker.auth.dto.UserLoginDTO;
import com.skybooker.common.AbstractIntegrationTest;
import com.skybooker.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RouteBoundaryTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        userToken = obtainUserToken();
        adminToken = obtainAdminToken();
    }

    @Test
    void userMe_acceptsUserToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void userMe_rejectsAdminToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminMe_acceptsAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminMe_rejectsUserToken() throws Exception {
        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void flightRoutes_arePublic() throws Exception {
        int status = mockMvc.perform(get("/api/flights"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, status, "Flight routes should not require authentication");
        assertNotEquals(403, status, "Flight routes should not require authorization");
    }

    @Test
    void userLogout_acceptsUserToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void userLogout_rejectsAdminToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminLogout_acceptsAdminToken() throws Exception {
        mockMvc.perform(post("/api/admin/logout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminLogout_rejectsUserToken() throws Exception {
        mockMvc.perform(post("/api/admin/logout")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userRoutes_requireUserToken() throws Exception {
        int status = mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getStatus();
        assertThat(status).isIn(200, 404);
    }

    @Test
    void userRoutes_rejectAdminToken() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void aiRoutes_rejectAdminToken() throws Exception {
        mockMvc.perform(get("/api/ai/sessions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void refundEndpoint_requiresUserToken() throws Exception {
        mockMvc.perform(post("/api/orders/1/refund")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void refundEndpoint_rejectsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/orders/1/refund"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void waitlistEndpoints_requireUserToken() throws Exception {
        mockMvc.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/waitlist/my")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void waitlistEndpoints_rejectUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/waitlist"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/waitlist/my"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/waitlist/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/waitlist/1/pay"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/waitlist/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issuedUserToken_isRejectedAfterAccountDisabled() throws Exception {
        try {
            jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE email = ?", "user1@example.com");

            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE email = ?", "user1@example.com");
        }
    }

    @Test
    void issuedAdminToken_isRejectedAfterAdminProfileDisabled() throws Exception {
        try {
            jdbcTemplate.update("UPDATE admin_user SET status = 'DISABLED' WHERE username = ?", "admin");

            mockMvc.perform(get("/api/admin/me")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbcTemplate.update("UPDATE admin_user SET status = 'ACTIVE' WHERE username = ?", "admin");
        }
    }

    @SuppressWarnings("unchecked")
    private String obtainUserToken() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setEmail("user1@example.com");
        dto.setPassword("User@123456");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map<String, Object>> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (String) data.get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private String obtainAdminToken() throws Exception {
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("Admin@123456");

        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map<String, Object>> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (String) data.get("accessToken");
    }
}
