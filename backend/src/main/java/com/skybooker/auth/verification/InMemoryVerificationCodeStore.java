package com.skybooker.auth.verification;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    private final Map<String, String> codes = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldownExpiry = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailyEmailCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> hourlyIpCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();

    @Override
    public void storeCode(String email, String scene, String code) {
        codes.put(key(email, scene), code);
    }

    @Override
    public String getCode(String email, String scene) {
        return codes.get(key(email, scene));
    }

    @Override
    public void removeCode(String email, String scene) {
        codes.remove(key(email, scene));
        failedAttempts.remove(key(email, scene));
    }

    @Override
    public boolean incrementFailedAttempts(String email, String scene) {
        int attempts = failedAttempts.getOrDefault(key(email, scene), 0) + 1;
        failedAttempts.put(key(email, scene), attempts);
        return attempts >= 5;
    }

    @Override
    public boolean checkResendCooldown(String email, String scene) {
        Long expiry = cooldownExpiry.get(key(email, scene));
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    @Override
    public void setResendCooldown(String email, String scene) {
        cooldownExpiry.put(key(email, scene), System.currentTimeMillis() + 60_000);
    }

    @Override
    public boolean checkDailyEmailLimit(String email) {
        String dayKey = LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ":" + email;
        return dailyEmailCounts.getOrDefault(dayKey, 0) >= 10;
    }

    @Override
    public void incrementDailyEmailCount(String email) {
        String dayKey = LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ":" + email;
        dailyEmailCounts.merge(dayKey, 1, Integer::sum);
    }

    @Override
    public boolean checkHourlyIpLimit(String ip) {
        String hourKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH")) + ":" + ip;
        return hourlyIpCounts.getOrDefault(hourKey, 0) >= 20;
    }

    @Override
    public void incrementHourlyIpCount(String ip) {
        String hourKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH")) + ":" + ip;
        hourlyIpCounts.merge(hourKey, 1, Integer::sum);
    }

    private String key(String email, String scene) {
        return scene + ":" + email;
    }
}
