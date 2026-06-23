package com.skybooker.auth.service;

import com.skybooker.auth.mail.MailSendException;
import com.skybooker.auth.mail.MailService;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.auth.verification.InMemoryVerificationCodeStore;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceEmailCodeTest {

    @Mock
    private AuthMapper authMapper;

    @Mock
    private MailService mailService;

    private InMemoryVerificationCodeStore codeStore;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        codeStore = new InMemoryVerificationCodeStore();
        authService = new AuthService(authMapper, null, null, codeStore, mailService);
    }

    @Test
    void successfulSendStoresUsableCodeAfterProviderAccepts() {
        String email = "new-user@example.com";

        authService.sendEmailCode(email, "REGISTER", "127.0.0.1");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationCode(eq(email), codeCaptor.capture(), eq("REGISTER"));
        String code = codeCaptor.getValue();

        assertThat(code).matches("\\d{6}");
        assertThat(codeStore.getCode(email, "REGISTER")).isEqualTo(code);
        verify(authMapper).insertVerificationCodeLog(email, "EMAIL", "REGISTER", "SUCCESS", "127.0.0.1");
    }

    @Test
    void providerFailureReturnsBusinessErrorWithoutUsableCodeOrCooldown() {
        String email = "new-user@example.com";
        doThrow(new MailSendException("sanitized failure"))
                .when(mailService).sendVerificationCode(eq(email), anyString(), eq("REGISTER"));

        assertThatThrownBy(() -> authService.sendEmailCode(email, "REGISTER", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_EMAIL_SEND_FAILED);
                    assertThat(exception.getCode()).isEqualTo(10016);
                });

        assertThat(codeStore.getCode(email, "REGISTER")).isNull();
        assertThat(codeStore.checkResendCooldown(email, "REGISTER")).isFalse();
        verify(authMapper).insertVerificationCodeLog(email, "EMAIL", "REGISTER", "FAILED", "127.0.0.1");
        verify(authMapper, never()).insertVerificationCodeLog(email, "EMAIL", "REGISTER", "SUCCESS", "127.0.0.1");
    }

    @Test
    void providerFailuresDoNotConsumeEmailDailySuccessfulSendLimit() {
        String email = "new-user@example.com";
        doThrow(new MailSendException("sanitized failure"))
                .when(mailService).sendVerificationCode(eq(email), anyString(), eq("REGISTER"));

        for (int i = 0; i < 10; i++) {
            String clientIp = "127.0.0." + i;
            assertThatThrownBy(() -> authService.sendEmailCode(email, "REGISTER", clientIp))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VERIFICATION_EMAIL_SEND_FAILED);
        }

        reset(mailService, authMapper);

        authService.sendEmailCode(email, "REGISTER", "127.0.0.99");

        assertThat(codeStore.getCode(email, "REGISTER")).isNotNull();
        verify(authMapper).insertVerificationCodeLog(email, "EMAIL", "REGISTER", "SUCCESS", "127.0.0.99");
    }

    @Test
    void providerFailuresCountTowardHourlyIpLimit() {
        doThrow(new MailSendException("sanitized failure"))
                .when(mailService).sendVerificationCode(anyString(), anyString(), eq("REGISTER"));

        for (int i = 0; i < 20; i++) {
            String email = "new-user-" + i + "@example.com";
            assertThatThrownBy(() -> authService.sendEmailCode(email, "REGISTER", "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VERIFICATION_EMAIL_SEND_FAILED);
        }

        reset(mailService, authMapper);

        assertThatThrownBy(() -> authService.sendEmailCode("blocked@example.com", "REGISTER", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IP_CODE_LIMIT_EXCEEDED));
        verifyNoInteractions(mailService);
    }
}
