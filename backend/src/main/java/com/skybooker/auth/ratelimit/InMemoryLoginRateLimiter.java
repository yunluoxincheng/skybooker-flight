package com.skybooker.auth.ratelimit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryLoginRateLimiter implements LoginRateLimiter {

    private static final long WINDOW_MS = 5 * 60 * 1000L;
    private static final int MAX_ATTEMPTS = 10;

    private final Map<String, long[]> counters = new ConcurrentHashMap<>();

    @Override
    public boolean isLimited(String accountKey, String ip) {
        return current("acct:" + accountKey) >= MAX_ATTEMPTS
                || current("ip:" + ip) >= MAX_ATTEMPTS;
    }

    @Override
    public void recordFailure(String accountKey, String ip) {
        bump("acct:" + accountKey);
        if (ip != null && !ip.isBlank()) {
            bump("ip:" + ip);
        }
    }

    @Override
    public void clear(String accountKey, String ip) {
        counters.remove("acct:" + accountKey);
        if (ip != null && !ip.isBlank()) {
            counters.remove("ip:" + ip);
        }
    }

    private long current(String key) {
        long[] entry = counters.get(key);
        if (entry == null || System.currentTimeMillis() > entry[1]) {
            return 0;
        }
        return entry[0];
    }

    private void bump(String key) {
        long now = System.currentTimeMillis();
        counters.compute(key, (k, existing) -> {
            if (existing == null || now > existing[1]) {
                return new long[]{1, now + WINDOW_MS};
            }
            existing[0]++;
            return existing;
        });
    }
}
