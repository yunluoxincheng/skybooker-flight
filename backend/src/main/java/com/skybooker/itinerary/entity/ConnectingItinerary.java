package com.skybooker.itinerary.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConnectingItinerary {
    private Long id;
    private Long firstFlightId;
    private Long secondFlightId;
    private String publishStatus;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
