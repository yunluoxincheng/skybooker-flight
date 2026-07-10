package com.skybooker.timezone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.order.dto.CreateOrderDTO;
import com.skybooker.waitlist.dto.CreateWaitlistDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * issue #139 时区回归测试。
 *
 * <p>复现条件：把 JVM 默认时区强制为 UTC（模拟 Docker 容器默认时区），把航班起飞时间设为
 * 业务时间（Asia/Shanghai）1 小时前（刚起飞）。
 *
 * <p>修复前（裸 {@code LocalDateTime.now()}，依赖 JVM 默认时区）：UTC 时钟比上海慢 8 小时，
 * “上海-1h” 仍晚于 “UTC now”，已起飞航班被判定为可售 → 下单成功 → 测试失败。
 *
 * <p>修复后（{@code LocalDateTime.now(businessClock)}，固定 Asia/Shanghai）：业务判断与 JVM 时区解耦，
 * 已起飞航班被正确拒绝（{@code FLIGHT_NOT_SELLABLE} = 30001）。
 *
 * <p>注：本类覆盖 JVM/Clock 层的可售性判断。公共搜索 SQL {@code NOW()} 谓词的时区正确性由
 * MySQL {@code --default-time-zone=+08:00} 保证（部署配置），不在此自动测试范围内。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BusinessTimezoneIntegrationTest {

    private static final int CODE_FLIGHT_NOT_SELLABLE = 30001;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock businessClock;

    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        userToken = loginAsUser();
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

    /** 模拟 issue #139 的 UTC 容器环境，返回原 JVM 时区供 finally 恢复。 */
    private TimeZone withUtcJvm() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        return original;
    }

    private void setFlightDeparture(Long flightId, LocalDateTime departure) {
        jdbcTemplate.update(
                "UPDATE flight SET departure_time = ?, arrival_time = ?, status = 'ON_TIME', "
                        + "publish_status = 'PUBLISHED' WHERE id = ?",
                departure, departure.plusHours(2), flightId);
    }

    private Long firstAvailableSeatId(Long flightId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM flight_seat WHERE flight_id = ? AND status = 'AVAILABLE' ORDER BY id LIMIT 1",
                Long.class, flightId);
    }

    @Test
    void createOrder_rejectsJustDepartedFlight_underUtcJvm() throws Exception {
        TimeZone original = withUtcJvm();
        try {
            // 航班业务时间 1 小时前起飞（刚起飞）。8h 时区偏差会让裸 now() 误判为可售。
            setFlightDeparture(1L, LocalDateTime.now(businessClock).minusHours(1));

            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setFlightId(1L);
            CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
            item.setPassengerId(1L);
            item.setSeatId(firstAvailableSeatId(1L));
            dto.setItems(List.of(item));

            mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CODE_FLIGHT_NOT_SELLABLE));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void createOrder_acceptsUpcomingFlight_underUtcJvm() throws Exception {
        TimeZone original = withUtcJvm();
        try {
            // 航班业务时间 2 小时后起飞（未起飞），应可正常下单（防止 Clock 改造误伤正常流程）
            setFlightDeparture(1L, LocalDateTime.now(businessClock).plusHours(2));

            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setFlightId(1L);
            CreateOrderDTO.OrderItemDTO item = new CreateOrderDTO.OrderItemDTO();
            item.setPassengerId(1L);
            item.setSeatId(firstAvailableSeatId(1L));
            dto.setItems(List.of(item));

            mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void createWaitlist_rejectsJustDepartedFlight_underUtcJvm() throws Exception {
        TimeZone original = withUtcJvm();
        try {
            setFlightDeparture(1L, LocalDateTime.now(businessClock).minusHours(1));

            CreateWaitlistDTO dto = new CreateWaitlistDTO();
            dto.setFlightId(1L);
            dto.setCabinClass("ECONOMY");
            dto.setPassengerIds(List.of(1L));

            mockMvc.perform(post("/api/waitlist")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CODE_FLIGHT_NOT_SELLABLE));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void searchFlights_excludesDepartedFlight_underUtcJvm() throws Exception {
        TimeZone original = withUtcJvm();
        try {
            // flight 1(MU5101)：业务时间 1 小时前已起飞，日期为业务今天
            LocalDateTime departed = LocalDateTime.now(businessClock).minusHours(1);
            jdbcTemplate.update(
                    "UPDATE flight SET departure_time = ?, arrival_time = ?, status = 'ON_TIME', "
                            + "publish_status = 'PUBLISHED' WHERE id = 1",
                    departed, departed.plusHours(2));

            // 公共搜索以 SQL NOW() 过滤已起飞航班；只有 MySQL NOW() 为北京时间（见 docs/11 业务时区）
            // 时才会排除 MU5101。此测试守护 default-time-zone=+08:00 配置不被回退。
            mockMvc.perform(get("/api/flights")
                            .param("departureDate", departed.toLocalDate().toString())
                            .param("page", "1").param("size", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[*].flightNo", not(hasItem("MU5101"))));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
