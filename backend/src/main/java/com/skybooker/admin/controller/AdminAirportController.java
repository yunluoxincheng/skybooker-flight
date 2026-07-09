package com.skybooker.admin.controller;

import com.skybooker.admin.dto.AirportDTO;
import com.skybooker.admin.dto.AirportUpdateDTO;
import com.skybooker.admin.dto.AdminKeywordStatusQueryDTO;
import com.skybooker.admin.service.AdminAirportService;
import com.skybooker.admin.vo.AirportVO;
import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端机场维护。{@code /api/admin/airports/**} 由 {@code SecurityConfig} 的
 * {@code /api/admin/**} 通配规则收敛为仅 ADMIN portal 可访问。
 * 候选项获取：航班新增/编辑表单以 {@code GET /api/admin/airports?status=ENABLED} 拉取下拉数据。
 */
@RestController
@RequestMapping("/api/admin/airports")
@RequiredArgsConstructor
public class AdminAirportController {

    private final AdminAirportService adminAirportService;

    @GetMapping
    public ApiResponse<PageResponse<AirportVO>> list(AdminKeywordStatusQueryDTO query) {
        return ApiResponse.success(adminAirportService.listAirports(query));
    }

    @PostMapping
    public ApiResponse<AirportVO> create(@Valid @RequestBody AirportDTO dto) {
        return ApiResponse.success(adminAirportService.createAirport(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<AirportVO> update(@PathVariable Long id, @Valid @RequestBody AirportUpdateDTO dto) {
        return ApiResponse.success(adminAirportService.updateAirport(id, dto));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        adminAirportService.disable(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        adminAirportService.enable(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminAirportService.delete(id);
        return ApiResponse.success();
    }
}
