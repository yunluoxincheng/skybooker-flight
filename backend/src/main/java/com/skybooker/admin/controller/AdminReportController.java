package com.skybooker.admin.controller;

import com.skybooker.admin.service.AdminReportService;
import com.skybooker.admin.vo.*;
import com.skybooker.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping("/sales-trend")
    public ApiResponse<List<SalesTrendVO>> getSalesTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String granularity) {
        return ApiResponse.success(adminReportService.getSalesTrend(startDate, endDate, granularity));
    }

    @GetMapping("/route-performance")
    public ApiResponse<List<RoutePerformanceVO>> getRoutePerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long airlineId,
            @RequestParam(required = false) String departureCity,
            @RequestParam(required = false) String arrivalCity,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(adminReportService.getRoutePerformance(
                startDate, endDate, airlineId, departureCity, arrivalCity, limit));
    }

    @GetMapping("/flight-load-factor")
    public ApiResponse<List<FlightLoadFactorVO>> getFlightLoadFactor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long airlineId,
            @RequestParam(required = false) String departureCity,
            @RequestParam(required = false) String arrivalCity,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(adminReportService.getFlightLoadFactor(
                startDate, endDate, airlineId, departureCity, arrivalCity, limit));
    }

    @GetMapping("/refund-trend")
    public ApiResponse<List<RefundTrendVO>> getRefundTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String granularity) {
        return ApiResponse.success(adminReportService.getRefundTrend(startDate, endDate, granularity));
    }

    @GetMapping("/waitlist-performance")
    public ApiResponse<WaitlistPerformanceVO> getWaitlistPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long airlineId,
            @RequestParam(required = false) String departureCity,
            @RequestParam(required = false) String arrivalCity) {
        return ApiResponse.success(adminReportService.getWaitlistPerformance(
                startDate, endDate, airlineId, departureCity, arrivalCity));
    }
}
