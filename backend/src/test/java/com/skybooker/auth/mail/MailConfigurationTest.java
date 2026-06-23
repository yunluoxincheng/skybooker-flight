package com.skybooker.auth.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withUserConfiguration(MailConfiguration.class);

    @Test
    void defaultsToLogMailWithoutResendCredentials() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MailService.class);
            assertThat(context.getBean(MailService.class)).isInstanceOf(LogMailService.class);
        });
    }

    @Test
    void selectsResendMailWhenProviderIsResend() {
        contextRunner
                .withPropertyValues(
                        "mail.provider=resend",
                        "mail.from=SkyBooker <noreply@example.com>",
                        "mail.resend.api-key=test-key",
                        "mail.resend.base-url=https://api.resend.test"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MailService.class);
                    assertThat(context.getBean(MailService.class)).isInstanceOf(ResendMailService.class);
                });
    }

    @Test
    void resendProviderFailsClearlyWhenApiKeyIsMissing() {
        contextRunner
                .withPropertyValues(
                        "mail.provider=resend",
                        "mail.from=SkyBooker <noreply@example.com>"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("RESEND_API_KEY");
                });
    }

    @Test
    void resendProviderFailsClearlyWhenFromAddressIsMissing() {
        contextRunner
                .withPropertyValues(
                        "mail.provider=resend",
                        "mail.resend.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("MAIL_FROM");
                });
    }

    @Test
    void testProfileUsesLogMailEvenWhenProviderRequestsResend() {
        contextRunner
                .withPropertyValues("spring.profiles.active=test", "mail.provider=resend")
                .run(context -> {
                    assertThat(context).hasSingleBean(MailService.class);
                    assertThat(context.getBean(MailService.class)).isInstanceOf(LogMailService.class);
                    assertThat(context).doesNotHaveBean(ResendMailService.class);
                });
    }
}
