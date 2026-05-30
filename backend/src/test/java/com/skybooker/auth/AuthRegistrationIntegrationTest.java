package com.skybooker.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.auth.dto.RegisterDTO;
import com.skybooker.auth.dto.ResetPasswordDTO;
import com.skybooker.auth.dto.SendEmailCodeDTO;
import com.skybooker.auth.verification.InMemoryVerificationCodeStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryVerificationCodeStore codeStore;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private String uniqueEmail() {
        return "test" + counter.incrementAndGet() + "_" + System.currentTimeMillis() + "@example.com";
    }

    @Test
    void sendRegisterCode_success() throws Exception {
        SendEmailCodeDTO dto = new SendEmailCodeDTO();
        dto.setEmail(uniqueEmail());
        dto.setScene("REGISTER");

        mockMvc.perform(post("/api/auth/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void sendRegisterCode_rejectsExistingEmail() throws Exception {
        SendEmailCodeDTO dto = new SendEmailCodeDTO();
        dto.setEmail("user1@example.com");
        dto.setScene("REGISTER");

        mockMvc.perform(post("/api/auth/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10009));
    }

    @Test
    void sendResetCode_rejectsUnknownEmail() throws Exception {
        SendEmailCodeDTO dto = new SendEmailCodeDTO();
        dto.setEmail("nonexistent@example.com");
        dto.setScene("RESET_PASSWORD");

        mockMvc.perform(post("/api/auth/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendCode_rejectsLoginScene() throws Exception {
        String json = "{\"email\":\"test@example.com\",\"scene\":\"LOGIN\"}";

        mockMvc.perform(post("/api/auth/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_success() throws Exception {
        String email = uniqueEmail();
        codeStore.storeCode(email, "REGISTER", "123456");

        RegisterDTO dto = new RegisterDTO();
        dto.setEmail(email);
        dto.setCode("123456");
        dto.setNickname("TestUser");
        dto.setPassword("Test1234");
        dto.setConfirmPassword("Test1234");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void register_rejectsDuplicateEmail() throws Exception {
        codeStore.storeCode("user1@example.com", "REGISTER", "123456");

        RegisterDTO dto = new RegisterDTO();
        dto.setEmail("user1@example.com");
        dto.setCode("123456");
        dto.setNickname("Duplicate");
        dto.setPassword("Test1234");
        dto.setConfirmPassword("Test1234");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10009));
    }

    @Test
    void register_rejectsInvalidCode() throws Exception {
        String email = uniqueEmail();
        codeStore.storeCode(email, "REGISTER", "654321");

        RegisterDTO dto = new RegisterDTO();
        dto.setEmail(email);
        dto.setCode("111111");
        dto.setNickname("TestUser");
        dto.setPassword("Test1234");
        dto.setConfirmPassword("Test1234");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10004));
    }

    @Test
    void register_rejectsPasswordMismatch() throws Exception {
        String email = uniqueEmail();
        codeStore.storeCode(email, "REGISTER", "123456");

        RegisterDTO dto = new RegisterDTO();
        dto.setEmail(email);
        dto.setCode("123456");
        dto.setNickname("TestUser");
        dto.setPassword("Test1234");
        dto.setConfirmPassword("Different1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10010));
    }

    @Test
    void resetPassword_success() throws Exception {
        // Register a new user first, then reset their password
        String email = uniqueEmail();
        codeStore.storeCode(email, "REGISTER", "111111");
        RegisterDTO regDto = new RegisterDTO();
        regDto.setEmail(email);
        regDto.setCode("111111");
        regDto.setNickname("ResetTestUser");
        regDto.setPassword("Test1234");
        regDto.setConfirmPassword("Test1234");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regDto)))
                .andExpect(status().isOk());

        codeStore.storeCode(email, "RESET_PASSWORD", "654321");

        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setEmail(email);
        dto.setCode("654321");
        dto.setNewPassword("NewPass1234");
        dto.setConfirmPassword("NewPass1234");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void resetPassword_rejectsInvalidCode() throws Exception {
        codeStore.storeCode("user1@example.com", "RESET_PASSWORD", "654321");

        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setEmail("user1@example.com");
        dto.setCode("000000");
        dto.setNewPassword("NewPass1234");
        dto.setConfirmPassword("NewPass1234");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10004));
    }

    @Test
    void resetPassword_rejectsPasswordMismatch() throws Exception {
        codeStore.storeCode("user1@example.com", "RESET_PASSWORD", "654321");

        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setEmail("user1@example.com");
        dto.setCode("654321");
        dto.setNewPassword("NewPass1234");
        dto.setConfirmPassword("Different1");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10010));
    }

    @Test
    void emailCodeEndpoints_arePublic() throws Exception {
        SendEmailCodeDTO dto = new SendEmailCodeDTO();
        dto.setEmail(uniqueEmail());
        dto.setScene("REGISTER");

        mockMvc.perform(post("/api/auth/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void meEndpoint_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
