package com.skybooker.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.AdminLoginDTO;
import com.skybooker.auth.dto.UserLoginDTO;
import com.skybooker.auth.service.AuthService;
import com.skybooker.auth.vo.LoginVO;
import com.skybooker.common.AbstractIntegrationTest;
import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.security.RefreshTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户端 /api/auth/refresh 与 /api/auth/logout 的端到端行为：
 * 双 token 签发、refresh 旋转、logout 作废、access/跨 portal token 拒绝。
 */
class AuthRefreshIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private AuthService authService;

    @SuppressWarnings("unchecked")
    private Map<String, Object> userLogin() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setEmail("user1@example.com");
        dto.setPassword("User@123456");
        return login("/api/auth/login", objectMapper.writeValueAsString(dto));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> adminLogin() throws Exception {
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("SkyBooker@Init2026!");
        return login("/api/admin/auth/login", objectMapper.writeValueAsString(dto));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        return (Map<String, Object>) response.getData();
    }

    private void refreshExpect(String endpoint, String token, int expectedStatus) throws Exception {
        mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void loginReturnsBothAccessAndRefreshToken() throws Exception {
        Map<String, Object> data = userLogin();

        assertNotNull(data.get("accessToken"));
        assertNotNull(data.get("refreshToken"));
        assertNotEquals(data.get("accessToken"), data.get("refreshToken"));
    }

    @Test
    void refreshIssuesNewPairAndRevokesOld() throws Exception {
        String oldRefresh = (String) userLogin().get("refreshToken");

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefresh))))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        assertEquals(200, response.getCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        String newRefresh = (String) data.get("refreshToken");
        assertNotNull(newRefresh);
        assertNotEquals(oldRefresh, newRefresh);

        // rotation：旧 refresh 已作废，再用应 401
        refreshExpect("/api/auth/refresh", oldRefresh, 401);
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String refreshToken = (String) userLogin().get("refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        refreshExpect("/api/auth/refresh", refreshToken, 401);
    }

    @Test
    void accessTokenCannotBeUsedAsRefresh() throws Exception {
        String accessToken = (String) userLogin().get("accessToken");
        refreshExpect("/api/auth/refresh", accessToken, 401);
    }

    @Test
    void rejectsAdminRefreshTokenOnUserEndpoint() throws Exception {
        String adminRefresh = (String) adminLogin().get("refreshToken");
        refreshExpect("/api/auth/refresh", adminRefresh, 401);
    }

    @SuppressWarnings("unchecked")
    @Test
    void revokeAllByUserInvalidatesExistingRefresh() throws Exception {
        Map<String, Object> loginData = userLogin();
        String refreshToken = (String) loginData.get("refreshToken");
        Long userId = ((Number) ((Map<String, Object>) loginData.get("user")).get("id")).longValue();

        // 模拟改密码触发的全设备登出：版本号递增，旧 refresh 的 tokenVer 失效
        refreshTokenStore.revokeAllByUser("USER", userId);

        refreshExpect("/api/auth/refresh", refreshToken, 401);
    }

    @Test
    void concurrentRefreshWithSameTokenAllowsAtMostOneSuccess() throws Exception {
        String refreshToken = (String) userLogin().get("refreshToken");

        // 同一 refresh 并发刷新：原子 consume 保证最多一个成功，杜绝双签发
        ExecutorService exec = Executors.newFixedThreadPool(2);
        Callable<LoginVO> task = () -> authService.refreshAccessToken(refreshToken);
        Future<LoginVO> f1 = exec.submit(task);
        Future<LoginVO> f2 = exec.submit(task);

        int successes = 0;
        try {
            f1.get();
            successes++;
        } catch (ExecutionException ignored) {
            // 另一个并发请求已消费该 jti
        }
        try {
            f2.get();
            successes++;
        } catch (ExecutionException ignored) {
            // 同上
        }
        exec.shutdown();

        assertThat(successes).isLessThanOrEqualTo(1);
    }
}
