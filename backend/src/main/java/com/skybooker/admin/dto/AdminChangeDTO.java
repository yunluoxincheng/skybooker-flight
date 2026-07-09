package com.skybooker.admin.dto;

import com.skybooker.order.dto.ChangeOrderDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminChangeDTO {

    @NotNull(message = "新航班ID不能为空")
    private Long newFlightId;

    @NotEmpty(message = "座位映射不能为空")
    @Valid
    private List<ChangeOrderDTO.SeatMapping> seatMappings;

    @NotBlank(message = "改签原因不能为空")
    @Size(max = 100, message = "改签原因不超过 100 字")
    private String reason;

    private Boolean force = false;

    public ChangeOrderDTO toChangeOrderDTO() {
        ChangeOrderDTO dto = new ChangeOrderDTO();
        dto.setNewFlightId(newFlightId);
        dto.setSeatMappings(seatMappings);
        return dto;
    }
}
