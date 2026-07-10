package com.skybooker.change.dto;

import com.skybooker.order.dto.CreateConnectingOrderDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class ConnectingChangeDTO {
    @NotNull private UUID clientRequestId;
    @NotEmpty @Size(min=1,max=2) @Valid private List<CreateConnectingOrderDTO.SegmentDTO> segments;
    @Size(max=255) private String reason;
    private Boolean force = false;
}
