package com.skybooker.common.security;

import java.time.Duration;

/**
 * Refresh token 的服务端登记表。
 * <p>
 * JWT 自带签名校验防伪造；本接口负责“作废”语义——logout 删除条目后，
 * 即使 refresh token 签名仍有效也无法继续刷新。
 * <p>
 * 以 portal + jti 为 key（而非 userId），支持同一用户多设备各自独立刷新/登出。
 */
public interface RefreshTokenStore {

    /** 记录一条 refresh token，TTL 到期后由底层自动清除。 */
    void store(String portal, String jti, Long userId, Duration ttl);

    /** 删除一条 refresh token（logout）。 */
    void revoke(String portal, String jti);

    /**
     * 当前用户的 token 版本（全设备登出计数器）。签发 refresh 时写入 token 的 tokenVer claim，
     * 校验时与之比对；不存在返回 0。
     */
    long currentVersion(String portal, Long userId);

    /**
     * 使该用户所有已签发的 refresh token 失效：递增版本号，旧 token 的 tokenVer 与新版本不符而被拒绝。
     * 用于改密码、安全事件等需要踢掉全部会话的场景。
     */
    void revokeAllByUser(String portal, Long userId);

    /**
     * 原子消费一条 refresh token：仅当 jti 存在且 userId 匹配时删除并返回 true。
     * 用于 refresh 旋转，保证并发下同一 jti 最多被消费一次（防止两个并发 /refresh 都签发成功）。
     */
    boolean consume(String portal, String jti, Long expectedUserId);
}
