package com.skybooker.auth.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {

    @Bean
    @Profile("!test")
    @ConditionalOnProperty(prefix = "mail", name = "provider", havingValue = "resend")
    public ResendMailService resendMailService(MailProperties properties, RestClient.Builder restClientBuilder) {
        return new ResendMailService(properties, restClientBuilder);
    }

    @Bean
    @ConditionalOnMissingBean(MailService.class)
    public LogMailService logMailService() {
        return new LogMailService();
    }
}
