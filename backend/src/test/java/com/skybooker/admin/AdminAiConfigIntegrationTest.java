package com.skybooker.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.admin.dto.AdminLoginDTO;
import com.skybooker.ai.config.DynamicLlmConfigProvider;
import com.skybooker.auth.dto.UserLoginDTO;
import com.skybooker.common.AbstractIntegrationTest;
import com.skybooker.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台 AI LLM 配置端点：GET 脱敏 / PUT 加密入库 + 写后生效 / 鉴权隔离 / 校验失败 10022。
 *
 * <p>注入测试用 {@code ai.config-enc-key} 使加密可用；每个测试方法前清理 DB 行 + 清 provider 缓存，
 * 保证从"DB 空"状态起步，避免 TTL 缓存跨方法残留。
 */
@TestPropertySource(properties = {
        "ai.config-enc-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AdminAiConfigIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DynamicLlmConfigProvider configProvider;

    @BeforeEach
    void clearConfig() {
        jdbcTemplate.update("DELETE FROM ai_llm_config WHERE id = 1");
        configProvider.invalidateCache();
    }

    @Test
    void get_returnsEnvDefaultWhenNoDbRecord() throws Exception {
        mockMvc.perform(get("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("env-default"))
                .andExpect(jsonPath("$.data.apiKey").value(""))   // env apiKey 空时不脱敏
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void put_thenGetReflectsUpdatedConfigWithMaskedApiKey() throws Exception {
        Map<String, Object> body = Map.of(
                "enabled", true,
                "baseUrl", "https://provider.example/v1",
                "apiKey", "sk-secret-key-12345",
                "model", "gpt-test");
        mockMvc.perform(put("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("db"))
                .andExpect(jsonPath("$.data.apiKey").value("sk****2345"))
                .andExpect(jsonPath("$.data.model").value("gpt-test"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        // 写后清缓存：再次 GET 仍是新值（无需等 TTL）
        mockMvc.perform(get("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(jsonPath("$.data.source").value("db"))
                .andExpect(jsonPath("$.data.apiKey").value("sk****2345"));
    }

    @Test
    void put_apiKeyNotPersistedInPlaintext() throws Exception {
        Map<String, Object> body = Map.of(
                "enabled", false,
                "baseUrl", "https://provider.example/v1",
                "apiKey", "sk-topsecret-99887766",
                "model", "gpt-test");
        mockMvc.perform(put("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        String cipher = jdbcTemplate.queryForObject(
                "SELECT api_key_cipher FROM ai_llm_config WHERE id = 1", String.class);
        assertThat(cipher).isNotBlank();
        assertThat(cipher).doesNotContain("sk-topsecret-99887766");
        assertThat(cipher).doesNotContain("topsecret");
    }

    @Test
    void put_omittingApiKeyKeepsExistingCipher() throws Exception {
        Map<String, Object> first = Map.of(
                "enabled", true, "baseUrl", "https://x.example/v1",
                "apiKey", "sk-first-key-1234567", "model", "m1");
        mockMvc.perform(put("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());
        String cipherAfterFirst = jdbcTemplate.queryForObject(
                "SELECT api_key_cipher FROM ai_llm_config WHERE id = 1", String.class);

        // 第二次 PUT 不带 apiKey 字段 → 保留旧密文，仅更新其他字段
        Map<String, Object> second = Map.of(
                "enabled", true, "baseUrl", "https://y.example/v1", "model", "m2");
        mockMvc.perform(put("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseUrl").value("https://y.example/v1"))
                .andExpect(jsonPath("$.data.model").value("m2"))
                .andExpect(jsonPath("$.data.apiKey").value("sk****4567"));   // 旧 key 脱敏

        String cipherAfterSecond = jdbcTemplate.queryForObject(
                "SELECT api_key_cipher FROM ai_llm_config WHERE id = 1", String.class);
        assertThat(cipherAfterSecond).isEqualTo(cipherAfterFirst);
    }

    @Test
    void put_enablingWithoutRequiredFields_returns10022() throws Exception {
        Map<String, Object> body = Map.of("enabled", true, "baseUrl", "", "model", "");
        mockMvc.perform(put("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10022));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_llm_config WHERE id = 1", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void put_invalidTimeoutRange_returns10022() throws Exception {
        Map<String, Object> body = Map.of(
                "enabled", false, "baseUrl", "https://x.example/v1",
                "model", "m", "timeoutMs", 0);
        mockMvc.perform(put("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10022));
    }

    @Test
    void put_enableWithoutApiKeyAndNoExistingKey_returns10022() throws Exception {
        // 首次启用（DB 空）省略 apiKey → 必须拒绝，避免写入空 key 遮蔽 env fallback
        Map<String, Object> body = Map.of(
                "enabled", true,
                "baseUrl", "https://provider.example/v1",
                "model", "gpt-test");
        mockMvc.perform(put("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10022));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_llm_config WHERE id = 1", Integer.class);
        assertThat(count).isEqualTo(0);   // 不落库，env fallback 不被遮蔽
    }

    @Test
    void put_blankApiKeyWhenDisabled_returns10022() throws Exception {
        // apiKey 传了就必须非空白（即使 enabled=false）—— 与 DTO 契约一致
        Map<String, Object> body = Map.of(
                "enabled", false,
                "baseUrl", "https://provider.example/v1",
                "model", "gpt-test",
                "apiKey", "   ");
        mockMvc.perform(put("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10022));
    }

    @Test
    void userToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/ai/llm-config")
                .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/ai/llm-config"))
                .andExpect(status().isUnauthorized());
    }

    @SuppressWarnings("unchecked")
    private String adminToken() throws Exception {
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("SkyBooker@Init2026!");
        return login("/api/admin/auth/login", dto);
    }

    @SuppressWarnings("unchecked")
    private String userToken() throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setEmail("user1@example.com");
        dto.setPassword("User@123456");
        return login("/api/auth/login", dto);
    }

    @SuppressWarnings("unchecked")
    private String login(String path, Object dto) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        return (String) ((Map<String, Object>) response.getData()).get("accessToken");
    }
}
