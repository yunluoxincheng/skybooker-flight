package com.skybooker.itinerary.vo;

import com.skybooker.flight.vo.FlightSeatVO;
import com.skybooker.flight.vo.FlightVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryVO {
    private Long id;
    private String journeyType;
    private List<FlightVO> segments;
    private String originCity;
    private String destinationCity;
    private String connectionAirportCode;
    private String connectionAirportName;
    private Integer connectionDurationMinutes;
    private Integer totalDurationMinutes;
    private BigDecimal estimatedAmount;
    private Integer availableSeats;
    private Boolean sellable;
    private List<SegmentAvailabilityVO> segmentAvailability;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentAvailabilityVO {
        private Long flightId;
        private List<FlightSeatVO> seats;
    }
}
