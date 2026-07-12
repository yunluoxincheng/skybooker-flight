package com.skybooker.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConnectingItinerarySummaryVO {
    private Long id;
    private String publishStatus;
    private Long firstFlightId;
    private String firstFlightNo;
    private String firstDepartureCity;
    private String firstArrivalCity;
    private LocalDateTime firstDepartureTime;
    private LocalDateTime firstArrivalTime;
    private Integer firstRemainingSeats;
    private Long secondFlightId;
    private String secondFlightNo;
    private String secondDepartureCity;
    private String secondArrivalCity;
    private LocalDateTime secondDepartureTime;
    private LocalDateTime secondArrivalTime;
    private Integer secondRemainingSeats;
    private Integer transferMinutes;
    private Integer availableSeats;
    private Boolean sellable;
    private String unavailableReason;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
