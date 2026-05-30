package com.skybooker.auth.verification;

public interface VerificationCodeStore {

    void storeCode(String email, String scene, String code);

    String getCode(String email, String scene);

    void removeCode(String email, String scene);

    boolean incrementFailedAttempts(String email, String scene);

    boolean checkResendCooldown(String email, String scene);

    void setResendCooldown(String email, String scene);

    boolean checkDailyEmailLimit(String email);

    void incrementDailyEmailCount(String email);

    boolean checkHourlyIpLimit(String ip);

    void incrementHourlyIpCount(String ip);
}
