package com.skybooker.auth.ratelimit;

/**
 * Tracks failed login attempts per account and per IP to throttle brute-force attacks.
 * Implementations must be profile-aware (Redis in prod, in-memory in tests).
 */
public interface LoginRateLimiter {

    /**
     * @return true if either the account or the IP has exceeded the failure threshold.
     */
    boolean isLimited(String accountKey, String ip);

    /**
     * Increment failure counters for the account and IP. Must be called on every login
     * failure, including "account not found" (to prevent enumeration bypass).
     */
    void recordFailure(String accountKey, String ip);

    /**
     * Reset counters on successful login.
     */
    void clear(String accountKey, String ip);
}
