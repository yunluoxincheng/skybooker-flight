package com.skybooker.waitlist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistVO {
    private Long id;
    private String waitlistNo;
    private Long userId;
    private Long flightId;
    private String flightNo;
    private String cabinClass;
    private Integer passengerCount;
    private BigDecimal payAmount;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime paidAt;
    private Long ticketOrderId;
    private BigDecimal refundAmount;
    private LocalDateTime refundTime;
    private String lastSkipReason;
    private LocalDateTime createdAt;
    private List<WaitlistPassengerVO> passengers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaitlistPassengerVO {
        private Long passengerId;
        private String passengerName;
        private String passengerType;
        private Long seatId;
        private String seatNo;
    }
}
