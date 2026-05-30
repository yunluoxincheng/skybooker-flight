package com.skybooker.order.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {
    private Long id;
    private String orderNo;
    private Long flightId;
    private String flightNo;
    private Long userId;
    private String userEmail;
    private String userNickname;
    private String status;
    private BigDecimal ticketAmount;
    private BigDecimal airportFee;
    private BigDecimal fuelFee;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;
    private List<OrderPassengerVO> passengers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPassengerVO {
        private Long passengerId;
        private String passengerName;
        private String passengerType;
        private Long seatId;
        private String seatNo;
        private BigDecimal ticketPrice;
    }
}
