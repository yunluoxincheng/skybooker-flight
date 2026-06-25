package com.skybooker.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void store(String portal, String jti, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(key(portal, jti), String.valueOf(userId), ttl);
    }

    @Override
    public void revoke(String portal, String jti) {
        redisTemplate.delete(key(portal, jti));
    }

    @Override
    public long currentVersion(String portal, Long userId) {
        String val = redisTemplate.opsForValue().get(versionKey(portal, userId));
        return val == null ? 0L : Long.parseLong(val);
    }

    @Override
    public void revokeAllByUser(String portal, Long userId) {
        // INCR 对不存在的 key 会新建并置 1，且不设 TTL —— 版本号需随用户长期保留，
        // 不能因过期回退导致旧 token 复活。
        redisTemplate.opsForValue().increment(versionKey(portal, userId));
    }

    @Override
    public boolean consume(String portal, String jti, Long expectedUserId) {
        // GETDEL 原子返回旧值并删除：并发下只有一个调用拿到非 null，从而保证旋转只成功一次。
        String val = redisTemplate.opsForValue().getAndDelete(key(portal, jti));
        if (val == null) {
            return false;
        }
        try {
            return expectedUserId.equals(Long.valueOf(val));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String key(String portal, String jti) {
        return PREFIX + portal + ":" + jti;
    }

    private String versionKey(String portal, Long userId) {
        return PREFIX + "ver:" + portal + ":" + userId;
    }
}
