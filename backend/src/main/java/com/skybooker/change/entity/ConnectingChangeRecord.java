package com.skybooker.change.entity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class ConnectingChangeRecord {
    private Long id; private Long orderId; private Long userId; private String clientRequestId; private BigDecimal oldTotalAmount;
    private BigDecimal newTotalAmount; private BigDecimal priceDifference; private BigDecimal changeFee; private String reason; private String status;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
