package com.skybooker.itinerary.controller;

import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.dto.FlightSearchDTO;
import com.skybooker.itinerary.dto.ItineraryQuoteDTO;
import com.skybooker.itinerary.service.ItineraryService;
import com.skybooker.itinerary.vo.ItineraryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.skybooker.itinerary.vo.FareCalendarVO;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
public class ItineraryController {
    private final ItineraryService service;
    @GetMapping("/search") public ApiResponse<PageResponse<ItineraryVO>> search(FlightSearchDTO dto) { return ApiResponse.success(service.search(dto)); }
    @PostMapping("/quote") public ApiResponse<ItineraryVO> quote(@Valid @RequestBody ItineraryQuoteDTO dto) { return ApiResponse.success(service.quote(dto)); }
    @GetMapping("/fare-calendar")
    public ApiResponse<List<FareCalendarVO>> fareCalendar(@RequestParam String departureCity,
            @RequestParam String arrivalCity, @RequestParam LocalDate startDate,
            @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(service.fareCalendar(departureCity, arrivalCity, startDate, days));
    }
    @GetMapping("/connecting/{id}")
    public ApiResponse<ItineraryVO> connectingDetail(@PathVariable Long id) {
        return ApiResponse.success(service.connectingDetail(id));
    }
}
