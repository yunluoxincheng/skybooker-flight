package com.skybooker.flight.controller;

import com.skybooker.common.response.ApiResponse;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.dto.FlightSearchDTO;
import com.skybooker.flight.service.FlightService;
import com.skybooker.flight.vo.FlightSeatVO;
import com.skybooker.flight.vo.FlightVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    @GetMapping
    public ApiResponse<PageResponse<FlightVO>> searchFlights(FlightSearchDTO dto) {
        return ApiResponse.success(flightService.searchFlights(dto));
    }

    @GetMapping("/{id}")
    public ApiResponse<FlightVO> getFlightDetail(@PathVariable Long id) {
        return ApiResponse.success(flightService.getFlightDetail(id));
    }

    @GetMapping("/{id}/seats")
    public ApiResponse<List<FlightSeatVO>> getFlightSeats(@PathVariable Long id) {
        return ApiResponse.success(flightService.getFlightSeats(id));
    }
}
