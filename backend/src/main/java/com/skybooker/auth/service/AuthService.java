package com.skybooker.auth.service;

import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.auth.dto.UserLoginDTO;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.auth.vo.LoginVO;
import com.skybooker.auth.vo.UserVO;
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
public class AuthService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginVO userLogin(UserLoginDTO dto) {
        User user = authMapper.findByEmail(dto.getEmail());
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!"USER".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.ACCOUNT_TYPE_MISMATCH);
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getEmail(), user.getRole(), "USER");

        return new LoginVO(
                token,
                "Bearer",
                jwtTokenProvider.getExpirationSeconds(),
                new UserVO(user.getId(), user.getEmail(), user.getNickname(), user.getRole())
        );
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
}
