package com.skybooker.admin.controller;

import com.skybooker.admin.dto.FlightCabinDTO;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.admin.service.AdminDashboardService;
import com.skybooker.admin.service.AdminFlightService;
import com.skybooker.admin.service.AdminService;
import com.skybooker.admin.vo.DashboardSummaryVO;
import com.skybooker.admin.vo.HotRouteVO;
import com.skybooker.admin.vo.OrderStatusDistributionVO;
import com.skybooker.admin.vo.UserAdminVO;
import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.vo.FlightCabinVO;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.order.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminFlightService adminFlightService;
    private final AdminService adminService;
    private final AdminDashboardService adminDashboardService;

    @GetMapping("/flights")
    public ApiResponse<PageResponse<FlightVO>> listFlights(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(adminFlightService.listFlights(page, size));
    }

    @PostMapping("/flights")
    public ApiResponse<FlightVO> createFlight(@Valid @RequestBody FlightFormDTO dto) {
        return ApiResponse.success(adminFlightService.createFlight(dto));
    }

    @PutMapping("/flights/{id}")
    public ApiResponse<FlightVO> updateFlight(@PathVariable Long id, @Valid @RequestBody FlightFormDTO dto) {
        return ApiResponse.success(adminFlightService.updateFlight(id, dto));
    }

    @PostMapping("/flights/{id}/publish")
    public ApiResponse<Void> publishFlight(@PathVariable Long id) {
        adminFlightService.publishFlight(id);
        return ApiResponse.success();
    }

    @PostMapping("/flights/{id}/unpublish")
    public ApiResponse<Void> unpublishFlight(@PathVariable Long id) {
        adminFlightService.unpublishFlight(id);
        return ApiResponse.success();
    }

    @GetMapping("/flights/{id}/cabins")
    public ApiResponse<List<FlightCabinVO>> getCabins(@PathVariable Long id) {
        return ApiResponse.success(adminFlightService.getCabins(id));
    }

    @PutMapping("/flights/{id}/cabins")
    public ApiResponse<List<FlightCabinVO>> setCabins(@PathVariable Long id,
                                                       @Valid @RequestBody List<FlightCabinDTO> cabins) {
        adminFlightService.setCabins(id, cabins);
        return ApiResponse.success(adminFlightService.getCabins(id));
    }

    @PostMapping("/flights/{id}/generate-seats")
    public ApiResponse<Void> generateSeats(@PathVariable Long id) {
        adminFlightService.generateSeats(id);
        return ApiResponse.success();
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderVO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(adminService.listOrders(page, size));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderVO> getOrderDetail(@PathVariable Long id) {
        return ApiResponse.success(adminService.getOrderDetail(id));
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserAdminVO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(adminService.listUsers(page, size));
    }

    @PostMapping("/users/{id}/disable")
    public ApiResponse<Void> disableUser(@PathVariable Long id) {
        adminService.disableUser(id);
        return ApiResponse.success();
    }

    @PostMapping("/users/{id}/enable")
    public ApiResponse<Void> enableUser(@PathVariable Long id) {
        adminService.enableUser(id);
        return ApiResponse.success();
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<DashboardSummaryVO> getDashboardSummary() {
        return ApiResponse.success(adminDashboardService.getSummary());
    }

    @GetMapping("/dashboard/hot-routes")
    public ApiResponse<List<HotRouteVO>> listDashboardHotRoutes(
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(adminDashboardService.listHotRoutes(limit));
    }

    @GetMapping("/dashboard/order-status")
    public ApiResponse<List<OrderStatusDistributionVO>> listDashboardOrderStatus() {
        return ApiResponse.success(adminDashboardService.listOrderStatusDistribution());
    }
}
