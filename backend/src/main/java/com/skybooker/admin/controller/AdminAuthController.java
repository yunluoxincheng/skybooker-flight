package com.skybooker.admin.controller;

import com.skybooker.admin.dto.AdminLoginDTO;
import com.skybooker.admin.service.AdminAuthService;
import com.skybooker.admin.vo.AdminLoginVO;
import com.skybooker.admin.vo.AdminVO;
import com.skybooker.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/auth/login")
    public ApiResponse<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO dto,
                                           HttpServletRequest request) {
        return ApiResponse.success(adminAuthService.adminLogin(dto, request.getRemoteAddr()));
    }

    @GetMapping("/me")
    public ApiResponse<AdminVO> me() {
        return ApiResponse.success(adminAuthService.getCurrentAdmin());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success();
    }
}
