package com.skybooker.auth.controller;

import com.skybooker.auth.dto.UserLoginDTO;
import com.skybooker.auth.service.AuthService;
import com.skybooker.auth.vo.LoginVO;
import com.skybooker.auth.vo.UserVO;
import com.skybooker.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody UserLoginDTO dto) {
        return ApiResponse.success(authService.userLogin(dto));
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
