package com.skybooker.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderDTO {
    @NotNull(message = "航班ID不能为空")
    private Long flightId;

    @NotEmpty(message = "订单项不能为空")
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO {
        @NotNull(message = "乘机人ID不能为空")
        private Long passengerId;
        @NotNull(message = "座位ID不能为空")
        private Long seatId;
    }
}
