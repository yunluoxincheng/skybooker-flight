package com.skybooker.auth.controller;

import com.skybooker.auth.dto.*;
import com.skybooker.auth.service.AuthService;
import com.skybooker.auth.vo.LoginVO;
import com.skybooker.auth.vo.UserVO;
import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody UserLoginDTO dto,
                                      HttpServletRequest request) {
        return ApiResponse.success(authService.userLogin(dto, ClientIpResolver.resolve(request)));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginVO> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refreshAccessToken(request.getRefreshToken()));
    }

    @PostMapping("/email-code")
    public ApiResponse<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeDTO dto,
                                           HttpServletRequest request) {
        String clientIp = ClientIpResolver.resolve(request);
        authService.sendEmailCode(dto.getEmail(), dto.getScene(), clientIp);
        return ApiResponse.success();
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return ApiResponse.success();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return ApiResponse.success(authService.getCurrentUser());
    }

    /**
     * 作废当前 refresh token。请求体可选：即使 access 已过期（未带 Authorization）也能调用，
     * 服务端只凭 body 里的 refresh token 完成作废。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        String refreshToken = request == null ? null : request.getRefreshToken();
        authService.logout(refreshToken);
        return ApiResponse.success();
    }

    @DeleteMapping("/account")
    public ApiResponse<Void> cancelAccount(@Valid @RequestBody(required = false) CancelAccountDTO dto,
                                           HttpServletRequest request) {
        authService.cancelCurrentAccount(dto, ClientIpResolver.resolve(request));
        return ApiResponse.success();
    }
}
