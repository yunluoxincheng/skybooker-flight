package com.skybooker.admin.service;

import com.skybooker.admin.dto.AdminLoginDTO;
import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.admin.vo.AdminLoginVO;
import com.skybooker.admin.vo.AdminVO;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.JwtTokenProvider;
import com.skybooker.common.security.LoginUserPrincipal;
import com.skybooker.common.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminMapper adminMapper;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AdminLoginVO adminLogin(AdminLoginDTO dto) {
        AdminUser adminUser = adminMapper.findByUsername(dto.getUsername());
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = authMapper.findById(adminUser.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!"ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.ACCOUNT_TYPE_MISMATCH);
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if ("DISABLED".equals(adminUser.getStatus())) {
            throw new BusinessException(ErrorCode.ADMIN_PROFILE_DISABLED);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getEmail(), user.getRole(), "ADMIN");

        return new AdminLoginVO(
                token,
                "Bearer",
                jwtTokenProvider.getExpirationSeconds(),
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
