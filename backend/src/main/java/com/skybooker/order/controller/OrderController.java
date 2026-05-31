package com.skybooker.order.controller;

import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.response.PageResponse;
import com.skybooker.order.dto.CreateOrderDTO;
import com.skybooker.order.service.OrderService;
import com.skybooker.order.vo.OrderVO;
import com.skybooker.refund.service.RefundService;
import com.skybooker.refund.vo.RefundVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RefundService refundService;

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
    public ApiResponse<RefundVO> refundOrder(@PathVariable Long id) {
        return ApiResponse.success(refundService.refundOrder(id));
    }
}
