package com.skybooker.auth.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisLoginRateLimiter implements LoginRateLimiter {

    private static final String ACCOUNT_PREFIX = "login:fail:acct:";
    private static final String IP_PREFIX = "login:fail:ip:";
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 10;

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isLimited(String accountKey, String ip) {
        return count(ACCOUNT_PREFIX + accountKey) >= MAX_ATTEMPTS
                || count(IP_PREFIX + ip) >= MAX_ATTEMPTS;
    }

    @Override
    public void recordFailure(String accountKey, String ip) {
        increment(ACCOUNT_PREFIX + accountKey);
        if (ip != null && !ip.isBlank()) {
            increment(IP_PREFIX + ip);
        }
    }

    @Override
    public void clear(String accountKey, String ip) {
        redisTemplate.delete(ACCOUNT_PREFIX + accountKey);
        if (ip != null && !ip.isBlank()) {
            redisTemplate.delete(IP_PREFIX + ip);
        }
    }

    private int count(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }

    private void increment(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW);
        }
    }
}
