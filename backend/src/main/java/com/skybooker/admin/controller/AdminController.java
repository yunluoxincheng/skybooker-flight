package com.skybooker.admin.controller;

import com.skybooker.admin.dto.AdminChangeDTO;
import com.skybooker.admin.dto.AdminCreateOrderDTO;
import com.skybooker.admin.dto.AdminCreateUserDTO;
import com.skybooker.admin.dto.AdminDeleteOrderDTO;
import com.skybooker.admin.dto.AdminFlightQueryDTO;
import com.skybooker.admin.dto.AdminNoteDTO;
import com.skybooker.admin.dto.AdminOrderQueryDTO;
import com.skybooker.admin.dto.AdminRefundDTO;
import com.skybooker.admin.dto.AdminVoidDTO;
import com.skybooker.admin.dto.FlightCabinDTO;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.admin.dto.PageQueryDTO;
import com.skybooker.admin.service.AdminDashboardService;
import com.skybooker.admin.service.AdminFlightService;
import com.skybooker.admin.service.AdminOrderService;
import com.skybooker.admin.service.AdminService;
import com.skybooker.admin.vo.AdminOrderDetailVO;
import com.skybooker.admin.vo.DashboardSummaryVO;
import com.skybooker.admin.vo.HotRouteVO;
import com.skybooker.admin.vo.OrderStatusDistributionVO;
import com.skybooker.admin.vo.UserAdminVO;
import com.skybooker.admin.vo.UserDeleteCheckVO;
import com.skybooker.change.entity.ChangeRecord;
import com.skybooker.change.vo.ChangeOrderResultVO;
import com.skybooker.change.vo.ChangeOptionVO;
import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.vo.FlightCabinVO;
import com.skybooker.flight.vo.FlightSeatVO;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.order.vo.OrderVO;
import com.skybooker.passenger.vo.PassengerVO;
import com.skybooker.refund.entity.RefundRecord;
import com.skybooker.refund.vo.RefundVO;
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
    private final AdminOrderService adminOrderService;

    @GetMapping("/flights")
    public ApiResponse<PageResponse<FlightVO>> listFlights(AdminFlightQueryDTO query) {
        return ApiResponse.success(adminFlightService.listFlights(query));
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
                                                       @RequestBody List<FlightCabinDTO> cabins) {
        adminFlightService.setCabins(id, cabins);
        return ApiResponse.success(adminFlightService.getCabins(id));
    }

    @PostMapping("/flights/{id}/generate-seats")
    public ApiResponse<Void> generateSeats(@PathVariable Long id) {
        adminFlightService.generateSeats(id);
        return ApiResponse.success();
    }

    @GetMapping("/flights/{id}/seats")
    public ApiResponse<List<FlightSeatVO>> getFlightSeats(@PathVariable Long id) {
        return ApiResponse.success(adminOrderService.listFlightSeats(id));
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderVO>> listOrders(AdminOrderQueryDTO query) {
        return ApiResponse.success(adminService.listOrders(query));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderVO> getOrderDetail(@PathVariable Long id) {
        return ApiResponse.success(adminService.getOrderDetail(id));
    }

    @GetMapping("/orders/{id}/detail")
    public ApiResponse<AdminOrderDetailVO> getEnhancedOrderDetail(@PathVariable Long id) {
        return ApiResponse.success(adminOrderService.getEnhancedOrderDetail(id));
    }

    @PostMapping("/orders")
    public ApiResponse<OrderVO> createOrderForUser(@Valid @RequestBody AdminCreateOrderDTO dto) {
        return ApiResponse.success(adminOrderService.createOrderForUser(dto));
    }

    @PostMapping("/orders/{id}/refund")
    public ApiResponse<RefundVO> refundOrder(@PathVariable Long id, @Valid @RequestBody AdminRefundDTO dto) {
        return ApiResponse.success(adminOrderService.refund(id, dto));
    }

    @PostMapping("/orders/{id}/change")
    public ApiResponse<ChangeOrderResultVO> changeOrder(@PathVariable Long id, @Valid @RequestBody AdminChangeDTO dto) {
        return ApiResponse.success(adminOrderService.change(id, dto));
    }

    @PostMapping("/orders/{id}/void")
    public ApiResponse<OrderVO> voidOrder(@PathVariable Long id, @Valid @RequestBody AdminVoidDTO dto) {
        return ApiResponse.success(adminOrderService.voidOrder(id, dto));
    }

    @DeleteMapping("/orders/{id}")
    public ApiResponse<OrderVO> deleteOrder(@PathVariable Long id,
                                            @RequestParam String type,
                                            @RequestParam(required = false) String reason,
                                            @Valid @RequestBody(required = false) AdminDeleteOrderDTO dto) {
        String effectiveReason = dto != null && dto.getReason() != null && !dto.getReason().isBlank()
                ? dto.getReason()
                : reason;
        return ApiResponse.success(adminOrderService.deleteOrderAsVoid(id, type, effectiveReason));
    }

    @PatchMapping("/orders/{id}/admin-note")
    public ApiResponse<OrderVO> updateAdminNote(@PathVariable Long id, @Valid @RequestBody AdminNoteDTO dto) {
        return ApiResponse.success(adminOrderService.updateAdminNote(id, dto));
    }

    @GetMapping("/orders/{id}/change-options")
    public ApiResponse<List<ChangeOptionVO>> listChangeOptions(@PathVariable Long id) {
        return ApiResponse.success(adminOrderService.listChangeOptions(id));
    }

    @GetMapping("/orders/{id}/refunds")
    public ApiResponse<List<RefundRecord>> listRefundRecords(@PathVariable Long id) {
        return ApiResponse.success(adminOrderService.listRefundRecords(id));
    }

    @GetMapping("/orders/{id}/changes")
    public ApiResponse<List<ChangeRecord>> listChangeRecords(@PathVariable Long id) {
        return ApiResponse.success(adminOrderService.listChangeRecords(id));
    }

    @GetMapping("/passengers")
    public ApiResponse<List<PassengerVO>> listPassengers(@RequestParam Long userId) {
        return ApiResponse.success(adminOrderService.listPassengersByUser(userId));
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserAdminVO>> listUsers(PageQueryDTO query) {
        return ApiResponse.success(adminService.listUsers(query));
    }

    @PostMapping("/users")
    public ApiResponse<UserAdminVO> createUser(@Valid @RequestBody AdminCreateUserDTO dto) {
        return ApiResponse.success(adminService.createUser(dto));
    }

    @GetMapping("/users/{id}/delete-check")
    public ApiResponse<UserDeleteCheckVO> deleteUserCheck(@PathVariable Long id) {
        return ApiResponse.success(adminService.getUserDeleteCheck(id));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ApiResponse.success();
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
