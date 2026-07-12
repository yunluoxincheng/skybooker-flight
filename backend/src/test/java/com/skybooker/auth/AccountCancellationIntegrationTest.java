package com.skybooker.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.auth.dto.RegisterDTO;
import com.skybooker.auth.dto.UserLoginDTO;
import com.skybooker.auth.verification.InMemoryVerificationCodeStore;
import com.skybooker.common.AbstractIntegrationTest;
import com.skybooker.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountCancellationIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String PASSWORD = "Cancel@123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InMemoryVerificationCodeStore codeStore;

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String email, String password) throws Exception {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setEmail(email);
        dto.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        return (Map<String, Object>) response.getData();
    }

    private String userToken(String email) throws Exception {
        return (String) login(email, PASSWORD).get("accessToken");
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"SkyBooker@Init2026!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (String) data.get("accessToken");
    }

    private Long createUser(String email) {
        return createUser(email, null);
    }

    private Long createUser(String email, String phone) {
        jdbcTemplate.update("""
                INSERT INTO users(email, phone, password_hash, nickname, role, status, email_verified)
                VALUES(?, ?, ?, ?, 'USER', 'NORMAL', 1)
                """, email, phone, passwordEncoder.encode(PASSWORD), "Cancel Test");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private String uniqueEmail() {
        return "cancel" + COUNTER.incrementAndGet() + "_" + System.currentTimeMillis() + "@example.com";
    }

    private String uniquePhone() {
        return "139" + String.format("%08d", COUNTER.incrementAndGet());
    }

    private void cancel(String token, String body, int expectedStatus) throws Exception {
        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void accountCancellation_rejectsUnauthenticatedAndAdminToken() throws Exception {
        mockMvc.perform(delete("/api/auth/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"SkyBooker@Init2026!\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountCancellation_rejectsMissingOrWrongPasswordWithoutChangingAccount() throws Exception {
        String email = uniqueEmail();
        Long userId = createUser(email);
        String token = userToken(email);

        cancel(token, "{}", 400);
        assertThat(userStatus(userId)).isEqualTo("NORMAL");

        cancel(token, "{\"currentPassword\":\"wrong-password\"}", 401);
        assertThat(userStatus(userId)).isEqualTo("NORMAL");
    }

    @Test
    void accountCancellation_rateLimitsRepeatedPasswordFailures() throws Exception {
        String email = uniqueEmail();
        Long userId = createUser(email);
        String token = userToken(email);

        for (int i = 0; i < 10; i++) {
            cancel(token, "{\"currentPassword\":\"wrong-password\"}", 401);
        }

        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(10017));
        assertThat(userStatus(userId)).isEqualTo("NORMAL");
    }

    @Test
    void accountCancellation_blocksActiveOrders() throws Exception {
        String email = uniqueEmail();
        Long userId = createUser(email);
        insertTicketOrder(userId, "PENDING_PAYMENT");

        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", "Bearer " + userToken(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40021));
        assertThat(userStatus(userId)).isEqualTo("NORMAL");
    }

    @Test
    void accountCancellation_blocksPendingWaitlist() throws Exception {
        String email = uniqueEmail();
        Long userId = createUser(email);
        insertWaitlist(userId, "WAITING");

        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", "Bearer " + userToken(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40022));
        assertThat(userStatus(userId)).isEqualTo("NORMAL");
    }

    @Test
    void accountCancellation_blocksProcessingRefundOrChange() throws Exception {
        String email = uniqueEmail();
        Long userId = createUser(email);
        Long orderId = insertTicketOrder(userId, "CANCELLED");
        jdbcTemplate.update("""
                INSERT INTO refund_record(order_id, user_id, reason, refund_amount, fee_amount, status)
                VALUES(?, ?, 'processing', 100.00, 0.00, 'PROCESSING')
                """, orderId, userId);

        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", "Bearer " + userToken(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40023));
        assertThat(userStatus(userId)).isEqualTo("NORMAL");
    }

    @Test
    void accountCancellation_succeedsAnonymizesAccountPreservesHistoryAndIgnoresSuppliedUserId() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        Long userId = createUser(email, phone);
        String otherEmail = uniqueEmail();
        Long otherUserId = createUser(otherEmail);
        Long orderId = insertTicketOrder(userId, "CANCELLED");
        Long passengerId = insertPassenger(userId);

        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", "Bearer " + userToken(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"userId\":" + otherUserId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> user = jdbcTemplate.queryForMap("""
                SELECT email, phone, nickname, avatar_url, status, email_verified, phone_verified
                FROM users WHERE id = ?
                """, userId);
        assertThat(user.get("status")).isEqualTo("DELETED");
        assertThat((String) user.get("email"))
                .startsWith("deleted-user-" + userId + "-")
                .endsWith("@deleted.skybooker.invalid");
        assertThat(user.get("phone")).isNull();
        assertThat(user.get("nickname")).isEqualTo("Cancelled User");
        assertThat(user.get("avatar_url")).isNull();
        assertThat(flagValue(user.get("email_verified"))).isZero();
        assertThat(flagValue(user.get("phone_verified"))).isZero();
        assertThat(userStatus(otherUserId)).isEqualTo("NORMAL");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_order WHERE id = ?", Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM passenger WHERE id = ?", Integer.class, passengerId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account_cancellation_log WHERE user_id = ? AND action = 'SELF_CANCEL'",
                Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'user_account_cancellation_log'
                  AND column_name IN ('current_password','password_hash','access_token','refresh_token','verification_code')
                """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE phone = ?", Integer.class, phone)).isZero();
    }

    @Test
    void accountCancellation_invalidatesTokensAndPreventsPreviousLogin() throws Exception {
        String email = uniqueEmail();
        createUser(email);
        Map<String, Object> login = login(email, PASSWORD);
        String accessToken = (String) login.get("accessToken");
        String refreshToken = (String) login.get("refreshToken");

        cancel(accessToken, "{\"currentPassword\":\"" + PASSWORD + "\"}", 200);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountCancellation_allowsPreviousEmailToRegisterAgainAndReleasesPhone() throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        createUser(email, phone);

        cancel(userToken(email), "{\"currentPassword\":\"" + PASSWORD + "\"}", 200);

        codeStore.storeCode(email, "REGISTER", "123456");
        RegisterDTO dto = new RegisterDTO();
        dto.setEmail(email);
        dto.setCode("123456");
        dto.setNickname("New Account");
        dto.setPassword("NewPass123");
        dto.setConfirmPassword("NewPass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND status = 'NORMAL'",
                Integer.class, email)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE phone = ?",
                Integer.class, phone)).isZero();
    }

    private String userStatus(Long userId) {
        return jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId);
    }

    private Long insertTicketOrder(Long userId, String status) {
        String orderNo = "TCANCEL" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO ticket_order(order_no, user_id, flight_id, status, total_amount)
                VALUES(?, ?, 1, ?, 100.00)
                """, orderNo, userId, status);
        return jdbcTemplate.queryForObject("SELECT id FROM ticket_order WHERE order_no = ?", Long.class, orderNo);
    }

    private void insertWaitlist(Long userId, String status) {
        jdbcTemplate.update("""
                INSERT INTO waitlist_order(waitlist_no, user_id, flight_id, passenger_count, cabin_class, pay_amount, status)
                VALUES(?, ?, 1, 1, 'ECONOMY', ?, ?)
                """, "WLCANCEL" + System.nanoTime(), userId, BigDecimal.valueOf(100), status);
    }

    private Long insertPassenger(Long userId) {
        String idCardNo = "ID" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO passenger(user_id, name, id_card_no, passenger_type, phone)
                VALUES(?, 'History Passenger', ?, 'ADULT', '13812345678')
                """, userId, idCardNo);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM passenger WHERE user_id = ? AND id_card_no = ?",
                Long.class, userId, idCardNo);
    }

    private int flagValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return ((Number) value).intValue();
    }
}
