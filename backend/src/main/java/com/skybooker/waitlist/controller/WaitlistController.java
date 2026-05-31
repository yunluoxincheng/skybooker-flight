package com.skybooker.waitlist.controller;

import com.skybooker.common.response.ApiResponse;
import com.skybooker.waitlist.dto.CreateWaitlistDTO;
import com.skybooker.waitlist.service.WaitlistService;
import com.skybooker.waitlist.vo.WaitlistVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    public ApiResponse<WaitlistVO> createWaitlist(@Valid @RequestBody CreateWaitlistDTO dto) {
        return ApiResponse.success(waitlistService.createWaitlist(dto));
    }

    @GetMapping("/my")
    public ApiResponse<List<WaitlistVO>> listMyWaitlists() {
        return ApiResponse.success(waitlistService.listMyWaitlists());
    }

    @GetMapping("/{id}")
    public ApiResponse<WaitlistVO> getWaitlistDetail(@PathVariable Long id) {
        return ApiResponse.success(waitlistService.getWaitlistDetail(id));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<WaitlistVO> payWaitlist(@PathVariable Long id) {
        return ApiResponse.success(waitlistService.payWaitlist(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<WaitlistVO> cancelWaitlist(@PathVariable Long id) {
        return ApiResponse.success(waitlistService.cancelWaitlist(id));
    }
}
