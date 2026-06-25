package com.skybooker.admin.controller;

import com.skybooker.admin.dto.AiLlmConfigDTO;
import com.skybooker.admin.service.AiLlmConfigService;
import com.skybooker.admin.vo.AiLlmConfigVO;
import com.skybooker.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员维护 AI LLM 运行时配置。{@code /api/admin/ai/**} 由 {@code SecurityConfig} 的
 * {@code /api/admin/**} 通配规则收敛为仅 ADMIN portal 可访问。
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiConfigController {

    private final AiLlmConfigService aiLlmConfigService;

    @GetMapping("/llm-config")
    public ApiResponse<AiLlmConfigVO> getConfig() {
        return ApiResponse.success(aiLlmConfigService.getConfig());
    }

    @PutMapping("/llm-config")
    public ApiResponse<AiLlmConfigVO> updateConfig(@Valid @RequestBody AiLlmConfigDTO dto) {
        return ApiResponse.success(aiLlmConfigService.updateConfig(dto));
    }
}
