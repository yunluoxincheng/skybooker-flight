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
    private String journeyType;
    private String flightNo;
    // 航班展示信息（join flight + airline + airport 填充，供订单页直接渲染）
    private String airlineName;
    private String departureCity;
    private String arrivalCity;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
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
    private String adminNote;
    private LocalDateTime createdAt;
    private List<OrderPassengerVO> passengers;
    private List<OrderSegmentVO> segments;

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSegmentVO {
        private Long id;
        private Integer segmentNo;
        private Long flightId;
        private String flightNo;
        private String airlineCode;
        private String airlineName;
        private String departureAirportCode;
        private String departureAirportName;
        private String departureCity;
        private String arrivalAirportCode;
        private String arrivalAirportName;
        private String arrivalCity;
        private LocalDateTime departureTime;
        private LocalDateTime arrivalTime;
        private BigDecimal ticketAmount;
        private List<OrderPassengerVO> passengers;
    }
}
