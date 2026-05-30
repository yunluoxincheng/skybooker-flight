package com.skybooker.auth.mail;

public interface MailService {
    void sendVerificationCode(String toEmail, String code, String scene);
}
