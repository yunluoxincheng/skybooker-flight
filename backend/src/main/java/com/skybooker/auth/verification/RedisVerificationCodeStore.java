package com.skybooker.auth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String CODE_PREFIX = "verify:code:";
    private static final String COOLDOWN_PREFIX = "verify:cooldown:";
    private static final String DAILY_EMAIL_PREFIX = "verify:daily:email:";
    private static final String HOURLY_IP_PREFIX = "verify:hourly:ip:";
    private static final String FAILED_PREFIX = "verify:failed:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final Duration DAILY_TTL = Duration.ofDays(1);
    private static final Duration HOURLY_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void storeCode(String email, String scene, String code) {
        String key = CODE_PREFIX + scene + ":" + email;
        redisTemplate.opsForValue().set(key, code, CODE_TTL);
    }

    @Override
    public String getCode(String email, String scene) {
        return redisTemplate.opsForValue().get(CODE_PREFIX + scene + ":" + email);
    }

    @Override
    public void removeCode(String email, String scene) {
        redisTemplate.delete(CODE_PREFIX + scene + ":" + email);
        redisTemplate.delete(FAILED_PREFIX + scene + ":" + email);
    }

    @Override
    public boolean incrementFailedAttempts(String email, String scene) {
        String key = FAILED_PREFIX + scene + ":" + email;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, CODE_TTL);
        }
        return attempts != null && attempts >= 5;
    }

    @Override
    public boolean checkResendCooldown(String email, String scene) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_PREFIX + scene + ":" + email));
    }

    @Override
    public void setResendCooldown(String email, String scene) {
        redisTemplate.opsForValue().set(COOLDOWN_PREFIX + scene + ":" + email, "1", COOLDOWN_TTL);
    }

    @Override
    public boolean checkDailyEmailLimit(String email) {
        String key = DAILY_EMAIL_PREFIX + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ":" + email;
        String count = redisTemplate.opsForValue().get(key);
        return count != null && Integer.parseInt(count) >= 10;
    }

    @Override
    public void incrementDailyEmailCount(String email) {
        String key = DAILY_EMAIL_PREFIX + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ":" + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, DAILY_TTL);
        }
    }

    @Override
    public boolean checkHourlyIpLimit(String ip) {
        String hour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        String key = HOURLY_IP_PREFIX + hour + ":" + ip;
        String count = redisTemplate.opsForValue().get(key);
        return count != null && Integer.parseInt(count) >= 20;
    }

    @Override
    public void incrementHourlyIpCount(String ip) {
        String hour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        String key = HOURLY_IP_PREFIX + hour + ":" + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, HOURLY_TTL);
        }
    }
}
