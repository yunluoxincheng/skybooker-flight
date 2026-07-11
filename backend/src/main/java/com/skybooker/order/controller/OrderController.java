package com.skybooker.order.controller;

import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.response.PageResponse;
import com.skybooker.order.dto.CreateOrderDTO;
import com.skybooker.order.dto.CreateConnectingOrderDTO;
import com.skybooker.order.service.ConnectingOrderService;
import com.skybooker.order.dto.RefundDTO;
import com.skybooker.order.service.OrderService;
import com.skybooker.order.vo.OrderVO;
import com.skybooker.refund.service.RefundService;
import com.skybooker.refund.vo.RefundVO;
import com.skybooker.change.dto.ConnectingChangeDTO;
import com.skybooker.change.service.ConnectingChangeService;
import com.skybooker.itinerary.vo.ItineraryVO;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RefundService refundService;
    private final ConnectingOrderService connectingOrderService;
    private final ConnectingChangeService connectingChangeService;

    @PostMapping("/connecting")
    public ApiResponse<OrderVO> createConnectingOrder(@Valid @RequestBody CreateConnectingOrderDTO dto) {
        return ApiResponse.success(connectingOrderService.create(dto));
    }

    @GetMapping("/{id}/connecting-change-options")
    public ApiResponse<List<ItineraryVO>> connectingChangeOptions(
            @PathVariable Long id,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate endDate) {
        return ApiResponse.success(connectingChangeService.options(id, startDate, endDate));
    }

    @PostMapping("/{id}/connecting-change")
    public ApiResponse<OrderVO> connectingChange(@PathVariable Long id, @Valid @RequestBody ConnectingChangeDTO dto) { return ApiResponse.success(connectingChangeService.change(id, dto)); }

    @PostMapping
    public ApiResponse<OrderVO> createOrder(@Valid @RequestBody CreateOrderDTO dto) {
        return ApiResponse.success(orderService.createOrder(dto));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<OrderVO> payOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.payOrder(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderVO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(orderService.listMyOrders(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderVO> getOrderDetail(@PathVariable Long id) {
        return ApiResponse.success(orderService.getOrderDetail(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderVO> cancelOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.cancelOrder(id));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<RefundVO> refundOrder(@PathVariable Long id,
                                             @Valid @RequestBody RefundDTO dto) {
        return ApiResponse.success(refundService.refundOrder(id, dto.getReason()));
    }
}
