package com.skybooker.itinerary.vo;

import lombok.Data;

@Data
public class ConnectingPairVO {
    private Long itineraryId;
    private Long firstFlightId;
    private Long secondFlightId;
}
