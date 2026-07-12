package com.skybooker.change.entity;

import lombok.Data;

@Data
public class ConnectingChangeSegmentSnapshot {
    private Integer segmentNo;
    private Long flightId;
    private String passengerSeats;
}
