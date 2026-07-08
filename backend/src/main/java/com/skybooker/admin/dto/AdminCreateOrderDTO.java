package com.skybooker.admin.dto;

import com.skybooker.order.dto.CreateOrderDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AdminCreateOrderDTO {

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;

    @NotNull(message = "航班ID不能为空")
    private Long flightId;

    @NotEmpty(message = "订单项不能为空")
    @Valid
    private List<CreateOrderDTO.OrderItemDTO> items;

    public CreateOrderDTO toCreateOrderDTO() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        dto.setItems(items);
        return dto;
    }
}
