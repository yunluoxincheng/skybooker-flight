package com.skybooker.common.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试环境的内存实现。测试 profile 关闭了 Redis 自动装配，
 * 此类保证 RefreshTokenStore 在无 Redis 时仍可注入。
 */
@Component
@Profile("test")
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private record Entry(long userId, long expireAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Map<String, Long> versions = new ConcurrentHashMap<>();

    @Override
    public void store(String portal, String jti, Long userId, Duration ttl) {
        store.put(key(portal, jti),
                new Entry(userId, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public void revoke(String portal, String jti) {
        store.remove(key(portal, jti));
    }

    @Override
    public long currentVersion(String portal, Long userId) {
        return versions.getOrDefault(versionKey(portal, userId), 0L);
    }

    @Override
    public void revokeAllByUser(String portal, Long userId) {
        versions.merge(versionKey(portal, userId), 1L, Long::sum);
    }

    @Override
    public boolean consume(String portal, String jti, Long expectedUserId) {
        // ConcurrentHashMap.remove 原子返回旧值：并发下只有一个调用拿到非 null。
        Entry entry = store.remove(key(portal, jti));
        if (entry == null || System.currentTimeMillis() > entry.expireAt()) {
            return false;
        }
        return expectedUserId.equals(entry.userId());
    }

    private String key(String portal, String jti) {
        return portal + ":" + jti;
    }

    private String versionKey(String portal, Long userId) {
        return portal + ":" + userId;
    }
}
