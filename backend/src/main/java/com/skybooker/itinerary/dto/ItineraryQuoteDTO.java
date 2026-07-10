package com.skybooker.itinerary.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ItineraryQuoteDTO {
    @NotEmpty
    @Size(min = 1, max = 2)
    private List<Long> segmentFlightIds;
    @NotEmpty
    private List<Long> passengerIds;
    private List<String> cabinPreferences;
}
