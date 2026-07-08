package com.skybooker.admin.dto;

import com.skybooker.order.dto.CreateOrderDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class AdminCreateOrderDTO {

    private Long targetUserId;

    private Long userId;

    @NotNull(message = "航班ID不能为空")
    private Long flightId;

    @NotEmpty(message = "订单项不能为空")
    @Valid
    private List<CreateOrderDTO.OrderItemDTO> items;

    public Long resolveTargetUserId() {
        if (targetUserId != null && userId != null && !Objects.equals(targetUserId, userId)) {
            return null;
        }
        return targetUserId != null ? targetUserId : userId;
    }

    public boolean hasConflictingUserAlias() {
        return targetUserId != null && userId != null && !Objects.equals(targetUserId, userId);
    }

    public CreateOrderDTO toCreateOrderDTO() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setFlightId(flightId);
        dto.setItems(items);
        return dto;
    }
}
