package com.skybooker.admin.service;

import com.skybooker.admin.dto.AdminLoginDTO;
import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.admin.vo.AdminLoginVO;
import com.skybooker.admin.vo.AdminVO;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.auth.ratelimit.LoginRateLimiter;
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

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final String PORTAL = "ADMIN";

    private final AdminMapper adminMapper;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginRateLimiter loginRateLimiter;

    public AdminLoginVO adminLogin(AdminLoginDTO dto, String ip) {
        if (loginRateLimiter.isLimited(dto.getUsername(), ip)) {
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMITED);
        }
        AdminUser adminUser = adminMapper.findByUsername(dto.getUsername());
        if (adminUser == null) {
            loginRateLimiter.recordFailure(dto.getUsername(), ip);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = authMapper.findById(adminUser.getUserId());
        if (user == null) {
            loginRateLimiter.recordFailure(dto.getUsername(), ip);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!"ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.ACCOUNT_TYPE_MISMATCH);
        }
        if (!"NORMAL".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if ("DISABLED".equals(adminUser.getStatus())) {
            throw new BusinessException(ErrorCode.ADMIN_PROFILE_DISABLED);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(dto.getUsername(), ip);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        loginRateLimiter.clear(dto.getUsername(), ip);
        return issueAdminLoginVO(user, adminUser);
    }

    /**
     * 用 refresh token 换取新的 access + refresh（旋转）。校验同用户端，portal 必须为 ADMIN，
     * 且管理员档案与关联账号均处于可用状态。
     */
    public AdminLoginVO refreshAdminAccessToken(String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        String portal = claims.get("loginPortal", String.class);
        Long userId = claims.get("userId", Long.class);
        String jti = claims.getId();
        if (!PORTAL.equals(portal) || userId == null || jti == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long tokenVer = claims.get("tokenVer", Long.class);
        if (tokenVer == null || tokenVer != refreshTokenStore.currentVersion(portal, userId)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        AdminUser adminUser = adminMapper.findByUserId(userId);
        User user = adminUser == null ? null : authMapper.findById(userId);
        if (adminUser == null || user == null
                || !"ADMIN".equals(user.getRole())
                || !"NORMAL".equals(user.getStatus())
                || "DISABLED".equals(adminUser.getStatus())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 原子消费旧 jti：并发下同一 jti 最多被消费一次，防止双签发
        if (!refreshTokenStore.consume(portal, jti, userId)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        return issueAdminLoginVO(user, adminUser);
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

    private AdminLoginVO issueAdminLoginVO(User user, AdminUser adminUser) {
        long tokenVer = refreshTokenStore.currentVersion(PORTAL, user.getId());
        TokenPair pair = jwtTokenProvider.issueTokenPair(
                user.getId(), user.getEmail(), user.getRole(), PORTAL, tokenVer);
        refreshTokenStore.store(PORTAL, pair.jti(), user.getId(),
                Duration.ofMillis(pair.refreshTtlMs()));
        return new AdminLoginVO(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                pair.accessExpiresInSec(),
                new AdminVO(adminUser.getId(), adminUser.getUserId(),
                        adminUser.getUsername(), adminUser.getRealName(), user.getRole())
        );
    }

    public AdminVO getCurrentAdmin() {
        LoginUserPrincipal principal = SecurityUtil.getCurrentPrincipal();
        if (principal == null || !"ADMIN".equals(principal.loginPortal())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        AdminUser adminUser = adminMapper.findByUserId(principal.userId());
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return new AdminVO(adminUser.getId(), adminUser.getUserId(),
                adminUser.getUsername(), adminUser.getRealName(), principal.role());
    }
}
