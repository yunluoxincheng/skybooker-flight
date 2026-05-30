package com.skybooker.auth.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogMailService implements MailService {

    @Override
    public void sendVerificationCode(String toEmail, String code, String scene) {
        log.info("[MailService] 发送验证码: email={}, scene={}, code={}", toEmail, scene, code);
    }
}
