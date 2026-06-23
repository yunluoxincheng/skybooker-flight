package com.skybooker.auth.service;

import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
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
import com.skybooker.common.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final VerificationCodeStore codeStore;
    private final MailService mailService;
    private final LoginRateLimiter loginRateLimiter;

    private static final SecureRandom RANDOM = new SecureRandom();

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

    public void sendEmailCode(String email, String scene, String clientIp) {
        if ("REGISTER".equals(scene)) {
            User existing = authMapper.findByEmail(email);
            if (existing != null) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
            }
        } else if ("RESET_PASSWORD".equals(scene)) {
            User existing = authMapper.findByEmail(email);
            if (existing == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
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
}
