package com.skybooker.admin.dto;

import com.skybooker.order.dto.CreateConnectingOrderDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Data
public class AdminCreateConnectingOrderDTO {
    private Long targetUserId;
    private Long userId;

    @NotNull
    private UUID clientRequestId;

    @NotEmpty
    @Size(min = 2, max = 2)
    @Valid
    private List<CreateConnectingOrderDTO.SegmentDTO> segments;

    public Long resolveTargetUserId() {
        if (targetUserId != null && userId != null && !Objects.equals(targetUserId, userId)) {
            return null;
        }
        return targetUserId != null ? targetUserId : userId;
    }

    public boolean hasConflictingUserAlias() {
        return targetUserId != null && userId != null && !Objects.equals(targetUserId, userId);
    }

    public CreateConnectingOrderDTO toCreateConnectingOrderDTO() {
        CreateConnectingOrderDTO dto = new CreateConnectingOrderDTO();
        dto.setClientRequestId(clientRequestId);
        dto.setSegments(segments);
        return dto;
    }
}
