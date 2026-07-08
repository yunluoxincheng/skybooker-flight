package com.skybooker.admin.controller;

import com.skybooker.admin.dto.AirlineDTO;
import com.skybooker.admin.dto.AirlineUpdateDTO;
import com.skybooker.admin.service.AdminAirlineService;
import com.skybooker.admin.vo.AirlineVO;
import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端航司维护。{@code /api/admin/airlines/**} 由 {@code SecurityConfig} 的
 * {@code /api/admin/**} 通配规则收敛为仅 ADMIN portal 可访问。
 * 候选项获取：航班新增/编辑表单以 {@code GET /api/admin/airlines?status=ENABLED} 拉取下拉数据。
 */
@RestController
@RequestMapping("/api/admin/airlines")
@RequiredArgsConstructor
public class AdminAirlineController {

    private final AdminAirlineService adminAirlineService;

    @GetMapping
    public ApiResponse<PageResponse<AirlineVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(adminAirlineService.listAirlines(keyword, status, page, size));
    }

    @PostMapping
    public ApiResponse<AirlineVO> create(@Valid @RequestBody AirlineDTO dto) {
        return ApiResponse.success(adminAirlineService.createAirline(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<AirlineVO> update(@PathVariable Long id, @Valid @RequestBody AirlineUpdateDTO dto) {
        return ApiResponse.success(adminAirlineService.updateAirline(id, dto));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        adminAirlineService.disable(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        adminAirlineService.enable(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminAirlineService.delete(id);
        return ApiResponse.success();
    }
}
