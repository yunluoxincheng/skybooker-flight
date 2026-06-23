package com.skybooker.auth.mail;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendMailServiceTest {

    @Test
    void sendVerificationCode_postsExpectedResendRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendMailService mailService = new ResendMailService(properties(), builder.build());

        server.expect(requestTo("https://api.resend.test/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("SkyBooker <noreply@example.com>"))
                .andExpect(jsonPath("$.to[0]").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("【SkyBooker】您的注册验证码(5 分钟内有效)"))
                .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString("123456")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        mailService.sendVerificationCode("user@example.com", "123456", "REGISTER");

        server.verify();
    }

    @Test
    void sendVerificationCode_sanitizesNon2xxProviderFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendMailService mailService = new ResendMailService(properties(), builder.build());

        server.expect(requestTo("https://api.resend.test/emails"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"raw-provider-body test-api-key Authorization\"}"));

        assertThatThrownBy(() -> mailService.sendVerificationCode("user@example.com", "123456", "RESET_PASSWORD"))
                .isInstanceOf(MailSendException.class)
                .hasMessageNotContaining("test-api-key")
                .hasMessageNotContaining("Authorization")
                .hasMessageNotContaining("raw-provider-body");

        server.verify();
    }

    @Test
    void sendVerificationCode_sanitizesTimeoutFailure() {
        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.resend.test")
                .requestFactory((uri, httpMethod) -> {
                    throw new ResourceAccessException("timeout with test-api-key Authorization");
                })
                .build();
        ResendMailService mailService = new ResendMailService(properties(), restClient);

        assertThatThrownBy(() -> mailService.sendVerificationCode("user@example.com", "123456", "REGISTER"))
                .isInstanceOf(MailSendException.class)
                .hasMessageNotContaining("test-api-key")
                .hasMessageNotContaining("Authorization");
    }

    private MailProperties properties() {
        MailProperties properties = new MailProperties();
        properties.setProvider("resend");
        properties.setFrom("SkyBooker <noreply@example.com>");
        properties.getResend().setApiKey("test-api-key");
        properties.getResend().setBaseUrl("https://api.resend.test");
        return properties;
    }
}
