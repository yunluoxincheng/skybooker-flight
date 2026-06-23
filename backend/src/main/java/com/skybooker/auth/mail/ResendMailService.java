package com.skybooker.auth.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

@Slf4j
public class ResendMailService implements MailService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final MailProperties properties;
    private final RestClient restClient;

    public ResendMailService(MailProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, buildRestClient(properties, restClientBuilder));
    }

    ResendMailService(MailProperties properties, RestClient restClient) {
        validateRequiredSettings(properties);
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public void sendVerificationCode(String toEmail, String code, String scene) {
        ResendEmailRequest request = new ResendEmailRequest(
                properties.getFrom(),
                List.of(toEmail),
                subjectFor(scene),
                htmlBody(code, scene)
        );

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getResend().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Resend verification email failed: status={}, scene={}", response.getStatusCode(), scene);
                throw new MailSendException("Verification email provider rejected the request");
            }
        } catch (RestClientResponseException e) {
            log.warn("Resend verification email failed: status={}, scene={}", e.getStatusCode(), scene);
            throw new MailSendException("Verification email provider rejected the request");
        } catch (ResourceAccessException e) {
            log.warn("Resend verification email failed because the provider was unreachable: scene={}", scene);
            throw new MailSendException("Verification email provider is unavailable");
        } catch (RestClientException e) {
            log.warn("Resend verification email failed because the provider request could not be completed: scene={}", scene);
            throw new MailSendException("Verification email provider request failed");
        }
    }

    private static RestClient buildRestClient(MailProperties properties, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return restClientBuilder
                .baseUrl(properties.getResend().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private static void validateRequiredSettings(MailProperties properties) {
        if (!StringUtils.hasText(properties.getFrom())) {
            throw new IllegalStateException("MAIL_FROM must be configured when MAIL_PROVIDER=resend");
        }
        if (properties.getResend() == null || !StringUtils.hasText(properties.getResend().getApiKey())) {
            throw new IllegalStateException("RESEND_API_KEY must be configured when MAIL_PROVIDER=resend");
        }
        if (!StringUtils.hasText(properties.getResend().getBaseUrl())) {
            throw new IllegalStateException("RESEND_BASE_URL must be configured when MAIL_PROVIDER=resend");
        }
    }

    private String subjectFor(String scene) {
        String label = switch (scene) {
            case "REGISTER" -> "注册验证码";
            case "RESET_PASSWORD" -> "找回密码验证码";
            default -> "验证码";
        };
        return "【" + appName() + "】您的" + label + "(5 分钟内有效)";
    }

    private String htmlBody(String code, String scene) {
        String purpose = switch (scene) {
            case "REGISTER" -> "注册账户";
            case "RESET_PASSWORD" -> "找回密码";
            default -> "验证身份";
        };

        return """
                <div style="font-family:Arial,'Microsoft YaHei',sans-serif;color:#333;line-height:1.6;max-width:480px;">
                  <h3 style="margin:0 0 12px;color:#1a73e8;">%1$s 验证码</h3>
                  <p>您正在%2$s,请使用以下验证码完成操作:</p>
                  <p style="font-size:36px;font-weight:700;letter-spacing:8px;color:#1a73e8;margin:16px 0;text-align:center;">%3$s</p>
                  <p>该验证码 <strong>5 分钟</strong> 内有效,请尽快使用,过期需重新获取。</p>
                  <p style="color:#666;font-size:13px;border-top:1px solid #eee;padding-top:12px;">请勿将验证码泄露给任何人。%1$s 团队绝不会向您索要验证码。</p>
                  <p style="color:#666;font-size:13px;">如非本人操作,请忽略此邮件,或及时修改密码以保障账户安全。</p>
                </div>
                """.formatted(appName(), purpose, code);
    }

    private String appName() {
        String from = properties.getFrom();
        if (from != null) {
            int lt = from.indexOf('<');
            if (lt > 0) {
                String name = from.substring(0, lt).trim();
                if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
                    name = name.substring(1, name.length() - 1).trim();
                }
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return "SkyBooker";
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String html) {
    }
}
