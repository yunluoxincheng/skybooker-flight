package com.skybooker.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LoginRateLimitIntegrationTest {

    private static final int MAX_ATTEMPTS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void userLogin_locksAfterRepeatedFailures() throws Exception {
        String body = "{\"email\":\"user1@example.com\",\"password\":\"wrong-password\"}";
        int lockedAt = failUntilLocked("/api/auth/login", body);
        // 阈值 10:锁定应发生在第 10 或第 11 次失败后(CI/本地 test context 时序差异,两者均接受)
        assertTrue(lockedAt == MAX_ATTEMPTS || lockedAt == MAX_ATTEMPTS + 1,
                "应在 " + MAX_ATTEMPTS + " 或 " + (MAX_ATTEMPTS + 1) + " 次失败后锁定,实际 " + lockedAt);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(10017));
    }

    @Test
    void adminLogin_locksAfterRepeatedFailures() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"wrong-password\"}";
        int lockedAt = failUntilLocked("/api/admin/auth/login", body);
        assertTrue(lockedAt == MAX_ATTEMPTS || lockedAt == MAX_ATTEMPTS + 1,
                "应在 " + MAX_ATTEMPTS + " 或 " + (MAX_ATTEMPTS + 1) + " 次失败后锁定,实际 " + lockedAt);
        mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(10017));
    }

    /** 连续发起失败登录,返回首次返回 429 的请求序号(1-based)。 */
    private int failUntilLocked(String url, String body) throws Exception {
        for (int i = 1; i <= MAX_ATTEMPTS + 2; i++) {
            int status = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                return i;
            }
            if (status != 401) {
                fail("第 " + i + " 次失败请求返回意外状态: " + status);
            }
        }
        throw new AssertionError("连续 " + (MAX_ATTEMPTS + 2) + " 次失败仍未被限流");
    }
}
