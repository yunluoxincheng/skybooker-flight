package com.skybooker.auth.controller;

import com.skybooker.auth.dto.*;
import com.skybooker.auth.service.AuthService;
import com.skybooker.auth.vo.LoginVO;
import com.skybooker.auth.vo.UserVO;
import com.skybooker.common.response.ApiResponse;
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
        return ApiResponse.success(authService.userLogin(dto, request.getRemoteAddr()));
    }

    @PostMapping("/email-code")
    public ApiResponse<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeDTO dto,
                                           HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
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

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success();
    }
}
