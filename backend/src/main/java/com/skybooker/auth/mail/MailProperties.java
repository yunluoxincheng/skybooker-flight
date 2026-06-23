package com.skybooker.auth.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mail")
public class MailProperties {

    private String provider = "log";
    private String from;
    private Resend resend = new Resend();

    @Data
    public static class Resend {
        private String apiKey;
        private String baseUrl = "https://api.resend.com";
    }
}
