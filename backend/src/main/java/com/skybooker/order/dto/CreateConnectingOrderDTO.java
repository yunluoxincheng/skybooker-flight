package com.skybooker.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateConnectingOrderDTO {
    @NotNull private UUID clientRequestId;
    @NotEmpty @Size(min = 2, max = 2) @Valid private List<SegmentDTO> segments;

    @Data
    public static class SegmentDTO {
        @NotNull private Long flightId;
        @NotEmpty @Valid private List<ItemDTO> items;
    }

    @Data
    public static class ItemDTO {
        @NotNull private Long passengerId;
        @NotNull private Long seatId;
    }
}
