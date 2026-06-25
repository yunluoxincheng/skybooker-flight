package com.skybooker.auth.service;

import com.skybooker.auth.dto.RegisterDTO;
import com.skybooker.auth.dto.ResetPasswordDTO;
import com.skybooker.auth.dto.UserLoginDTO;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mail.MailSendException;
import com.skybooker.auth.mail.MailService;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.auth.ratelimit.LoginRateLimiter;
import com.skybooker.auth.verification.VerificationCodeStore;
import com.skybooker.auth.vo.LoginVO;
import com.skybooker.auth.vo.UserVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.JwtTokenProvider;
import com.skybooker.common.security.LoginUserPrincipal;
import com.skybooker.common.security.RefreshTokenStore;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.common.security.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PORTAL = "USER";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final VerificationCodeStore codeStore;
    private final MailService mailService;
    private final LoginRateLimiter loginRateLimiter;

    public LoginVO userLogin(UserLoginDTO dto, String ip) {
        if (loginRateLimiter.isLimited(dto.getEmail(), ip)) {
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMITED);
        }
        User user = authMapper.findByEmail(dto.getEmail());
        if (user == null) {
            loginRateLimiter.recordFailure(dto.getEmail(), ip);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!"USER".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.ACCOUNT_TYPE_MISMATCH);
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(dto.getEmail(), ip);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        loginRateLimiter.clear(dto.getEmail(), ip);
        return issueLoginVO(user);
    }

    /**
     * 用 refresh token 换取新的 access + refresh（旋转）。refresh 必须签名有效、portal 匹配、
     * 仍在 RefreshTokenStore 中、且账号未被禁用，任一不满足统一抛 REFRESH_TOKEN_INVALID。
     */
    public LoginVO refreshAccessToken(String refreshToken) {
        RefreshClaims claims = parseRefresh(refreshToken);
        if (!PORTAL.equals(claims.portal())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 全设备登出后版本号被递增，旧 token 的 tokenVer 不再匹配
        long currentVer = refreshTokenStore.currentVersion(claims.portal(), claims.userId());
        if (claims.tokenVer() != currentVer) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = authMapper.findById(claims.userId());
        if (user == null || !"USER".equals(user.getRole())
                || "DISABLED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 原子消费旧 jti：并发下同一 jti 最多被消费一次，防止双签发
        if (!refreshTokenStore.consume(claims.portal(), claims.jti(), claims.userId())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        return issueLoginVO(user);
    }

    /**
     * 作废当前 refresh token。幂等：token 为空、不可解析或已作废均返回成功。
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            Claims claims = jwtTokenProvider.parseRefreshToken(refreshToken);
            String portal = claims.get("loginPortal", String.class);
            String jti = claims.getId();
            if (portal != null && jti != null) {
                refreshTokenStore.revoke(portal, jti);
            }
        } catch (JwtException | IllegalArgumentException e) {
            // token 已不可解析，refresh 记录可能已过期或不存在，幂等返回
        }
    }

    private LoginVO issueLoginVO(User user) {
        long tokenVer = refreshTokenStore.currentVersion(PORTAL, user.getId());
        TokenPair pair = jwtTokenProvider.issueTokenPair(
                user.getId(), user.getEmail(), user.getRole(), PORTAL, tokenVer);
        refreshTokenStore.store(PORTAL, pair.jti(), user.getId(),
                Duration.ofMillis(pair.refreshTtlMs()));
        return new LoginVO(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                pair.accessExpiresInSec(),
                new UserVO(user.getId(), user.getEmail(), user.getNickname(), user.getRole())
        );
    }

    private RefreshClaims parseRefresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        String portal = claims.get("loginPortal", String.class);
        Long userId = claims.get("userId", Long.class);
        String jti = claims.getId();
        Long tokenVer = claims.get("tokenVer", Long.class);
        if (portal == null || userId == null || jti == null || tokenVer == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new RefreshClaims(portal, userId, jti, tokenVer);
    }

    public UserVO getCurrentUser() {
        LoginUserPrincipal principal = SecurityUtil.getCurrentPrincipal();
        if (principal == null || !"USER".equals(principal.loginPortal())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        User user = authMapper.findByEmail(principal.email());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new UserVO(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
    }

    public void sendEmailCode(String email, String scene, String clientIp) {
        boolean resetUnknownEmail = false;
        if ("REGISTER".equals(scene)) {
            User existing = authMapper.findByEmail(email);
            if (existing != null) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
            }
        } else if ("RESET_PASSWORD".equals(scene)) {
            User existing = authMapper.findByEmail(email);
            if (existing == null) {
                // 防邮箱枚举:不存在也走完整冷却/限流流程,只是不发码
                resetUnknownEmail = true;
            }
        }

        if (codeStore.checkResendCooldown(email, scene)) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_SEND_TOO_FREQUENT);
        }
        if (codeStore.checkDailyEmailLimit(email)) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_DAILY_LIMIT);
        }
        if (codeStore.checkHourlyIpLimit(clientIp)) {
            throw new BusinessException(ErrorCode.IP_CODE_LIMIT_EXCEEDED);
        }

        if (resetUnknownEmail) {
            // 不存在邮箱:设冷却 + 每日邮箱计数 + IP 计数(与存在一致),但不发码、不存码
            codeStore.setResendCooldown(email, scene);
            codeStore.incrementDailyEmailCount(email);
            codeStore.incrementHourlyIpCount(clientIp);
            return;
        }

        String code = generateCode();
        try {
            mailService.sendVerificationCode(email, code, scene);
        } catch (MailSendException e) {
            codeStore.incrementHourlyIpCount(clientIp);
            authMapper.insertVerificationCodeLog(email, "EMAIL", scene, "FAILED", clientIp);
            throw new BusinessException(ErrorCode.VERIFICATION_EMAIL_SEND_FAILED);
        }

        codeStore.storeCode(email, scene, code);
        codeStore.setResendCooldown(email, scene);
        codeStore.incrementDailyEmailCount(email);
        codeStore.incrementHourlyIpCount(clientIp);
        authMapper.insertVerificationCodeLog(email, "EMAIL", scene, "SUCCESS", clientIp);
    }

    public void register(RegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        User existing = authMapper.findByEmail(dto.getEmail());
        if (existing != null) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        validateCode(dto.getEmail(), "REGISTER", dto.getCode());

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setRole("USER");
        user.setStatus("NORMAL");
        user.setEmailVerified(true);
        authMapper.insertUser(user);

        codeStore.removeCode(dto.getEmail(), "REGISTER");
    }

    public void resetPassword(ResetPasswordDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        User user = authMapper.findByEmail(dto.getEmail());
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        validateCode(dto.getEmail(), "RESET_PASSWORD", dto.getCode());

        authMapper.updatePassword(user.getId(), passwordEncoder.encode(dto.getNewPassword()));
        codeStore.removeCode(dto.getEmail(), "RESET_PASSWORD");
        // 改密码后作废该用户所有已签发 refresh token，强制其他设备重新登录
        refreshTokenStore.revokeAllByUser(PORTAL, user.getId());
    }

    private void validateCode(String email, String scene, String inputCode) {
        String storedCode = codeStore.getCode(email, scene);
        if (storedCode == null || !storedCode.equals(inputCode)) {
            boolean invalidated = codeStore.incrementFailedAttempts(email, scene);
            if (invalidated) {
                codeStore.removeCode(email, scene);
            }
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
        }
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private record RefreshClaims(String portal, Long userId, String jti, long tokenVer) {
    }
}
