package com.skybooker.order.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketOrderSegment {
    private Long id;
    private Long orderId;
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
}
