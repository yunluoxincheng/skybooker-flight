package com.skybooker.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 管理端航司/机场 CRUD 集成测试（issue #90 / KI-009-BE）。
 *
 * <p>数据隔离策略同 {@code PassengerIntegrationTest}：测试库不做回滚，
 * 靠 {@link #uniqueCode(String)} 生成跨次运行唯一的 code 规避 UNIQUE 约束；
 * 读/搜索/重复/鉴权类用例直接基于 {@code base-air-data.sql} 种子数据（MU/SHA 等）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminAirlineAirportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String userToken;

    private static final AtomicInteger counter = new AtomicInteger();

    private String uniqueCode(String prefix) {
        // 20 字符上限：prefix + nanoTime 尾段 + 自增计数，保证跨测试方法与跨次运行唯一
        String nano = Long.toString(System.nanoTime());
        String tail = nano.substring(Math.max(0, nano.length() - 10));
        String code = prefix + tail + counter.incrementAndGet();
        return code.length() > 20 ? code.substring(0, 20) : code;
    }

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAsAdmin();
        userToken = loginAsUser();
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"SkyBooker@Init2026!\"}"))
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

    private Long createAirline(String code, String name) throws Exception {
        String body = String.format("{\"code\":\"%s\",\"name\":\"%s\"}", code, name);
        MvcResult result = mockMvc.perform(post("/api/admin/airlines")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private Long createAirport(String code, String name, String city) throws Exception {
        String body = String.format("{\"code\":\"%s\",\"name\":\"%s\",\"city\":\"%s\"}", code, name, city);
        MvcResult result = mockMvc.perform(post("/api/admin/airports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    // ---- Airline Management ----

    @Test
    void listAirlines_returnsSeedRecords() throws Exception {
        mockMvc.perform(get("/api/admin/airlines")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.data.records[0].code").exists())
                .andExpect(jsonPath("$.data.records[0].name").exists())
                .andExpect(jsonPath("$.data.records[0].status").exists());
    }

    @Test
    void listAirlines_filtersByKeyword() throws Exception {
        mockMvc.perform(get("/api/admin/airlines")
                        .param("keyword", "东方")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].name").value(containsString("东方")));
    }

    @Test
    void createAirline_success() throws Exception {
        String code = uniqueCode("AL");
        String body = String.format(
                "{\"code\":\"%s\",\"name\":\"测试航空\",\"logoUrl\":\"https://example.com/logo.png\"}", code);
        mockMvc.perform(post("/api/admin/airlines")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.name").value("测试航空"))
                .andExpect(jsonPath("$.data.logoUrl").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    @Test
    void createAirline_rejectsDuplicateCode() throws Exception {
        // 种子数据已含 MU（东方航空）
        String body = "{\"code\":\"MU\",\"name\":\"重复航空\"}";
        mockMvc.perform(post("/api/admin/airlines")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40010));
    }

    @Test
    void updateAirline_successWithoutCode() throws Exception {
        String originalCode = uniqueCode("AL");
        Long id = createAirline(originalCode, "更新前航空");

        // 编辑请求按契约不携带 code（code 为稳定标识）；验证仍能更新，且 code 保持原值
        String updateBody = "{\"name\":\"更新后航空\",\"logoUrl\":\"https://example.com/new.png\"}";
        mockMvc.perform(put("/api/admin/airlines/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(originalCode))
                .andExpect(jsonPath("$.data.name").value("更新后航空"))
                .andExpect(jsonPath("$.data.logoUrl").value("https://example.com/new.png"));
    }

    @Test
    void updateAirline_notFound() throws Exception {
        String body = "{\"name\":\"不存在\"}";
        mockMvc.perform(put("/api/admin/airlines/99999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void disableAndEnableAirline() throws Exception {
        String code = uniqueCode("AL");
        Long id = createAirline(code, "禁用测试航空");

        mockMvc.perform(post("/api/admin/airlines/" + id + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/airlines")
                        .param("status", "DISABLED")
                        .param("keyword", code)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("DISABLED"));

        mockMvc.perform(post("/api/admin/airlines/" + id + "/enable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/airlines")
                        .param("status", "ENABLED")
                        .param("keyword", code)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("ENABLED"));
    }

    // ---- Airport Management ----

    @Test
    void listAirports_returnsSeedRecords() throws Exception {
        mockMvc.perform(get("/api/admin/airports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$.data.records[0].city").exists());
    }

    @Test
    void listAirports_filtersByCity() throws Exception {
        mockMvc.perform(get("/api/admin/airports")
                        .param("keyword", "北京")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.records[0].city").value(containsString("北京")));
    }

    @Test
    void createAirport_success() throws Exception {
        String code = uniqueCode("AP");
        String body = String.format(
                "{\"code\":\"%s\",\"name\":\"测试机场\",\"city\":\"测试市\",\"province\":\"测试省\"}", code);
        mockMvc.perform(post("/api/admin/airports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.name").value("测试机场"))
                .andExpect(jsonPath("$.data.city").value("测试市"))
                .andExpect(jsonPath("$.data.province").value("测试省"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    @Test
    void createAirport_rejectsDuplicateCode() throws Exception {
        // 种子数据已含 SHA（上海虹桥）
        String body = "{\"code\":\"SHA\",\"name\":\"重复机场\",\"city\":\"上海\"}";
        mockMvc.perform(post("/api/admin/airports")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40011));
    }

    @Test
    void updateAirport_successWithoutCode() throws Exception {
        String originalCode = uniqueCode("AP");
        Long id = createAirport(originalCode, "更新前机场", "旧城市");

        // 编辑请求按契约不携带 code（code 为稳定标识）；验证仍能更新，且 code 保持原值
        String updateBody = "{\"name\":\"更新后机场\",\"city\":\"新城市\",\"province\":\"新省\"}";
        mockMvc.perform(put("/api/admin/airports/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(originalCode))
                .andExpect(jsonPath("$.data.name").value("更新后机场"))
                .andExpect(jsonPath("$.data.city").value("新城市"))
                .andExpect(jsonPath("$.data.province").value("新省"));
    }

    @Test
    void disableAndEnableAirport() throws Exception {
        String code = uniqueCode("AP");
        Long id = createAirport(code, "禁用测试机场", "禁用市");

        mockMvc.perform(post("/api/admin/airports/" + id + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/airports")
                        .param("status", "DISABLED")
                        .param("keyword", code)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("DISABLED"));

        mockMvc.perform(post("/api/admin/airports/" + id + "/enable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/airports")
                        .param("status", "ENABLED")
                        .param("keyword", code)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("ENABLED"));
    }

    // ---- Auth Isolation ----

    @Test
    void airlineAirportEndpoints_rejectUserToken() throws Exception {
        mockMvc.perform(get("/api/admin/airlines")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/airports")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void airlineAirportEndpoints_rejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/airlines"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/airports"))
                .andExpect(status().isUnauthorized());
    }
}
