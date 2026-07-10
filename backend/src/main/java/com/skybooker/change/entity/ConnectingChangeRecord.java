package com.skybooker.change.entity;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class ConnectingChangeRecord {
    private Long id; private Long orderId; private Long userId; private String clientRequestId; private BigDecimal oldTotalAmount;
    private BigDecimal newTotalAmount; private BigDecimal priceDifference; private BigDecimal changeFee; private String reason; private String status;
}
