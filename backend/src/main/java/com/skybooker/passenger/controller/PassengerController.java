package com.skybooker.passenger.controller;

import com.skybooker.common.response.ApiResponse;
import com.skybooker.passenger.dto.PassengerDTO;
import com.skybooker.passenger.service.PassengerService;
import com.skybooker.passenger.vo.PassengerVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/passengers")
@RequiredArgsConstructor
public class PassengerController {

    private final PassengerService passengerService;

    @GetMapping
    public ApiResponse<List<PassengerVO>> list() {
        return ApiResponse.success(passengerService.listMyPassengers());
    }

    @PostMapping
    public ApiResponse<PassengerVO> create(@Valid @RequestBody PassengerDTO dto) {
        return ApiResponse.success(passengerService.createPassenger(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<PassengerVO> update(@PathVariable Long id, @Valid @RequestBody PassengerDTO dto) {
        return ApiResponse.success(passengerService.updatePassenger(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        passengerService.deletePassenger(id);
        return ApiResponse.success();
    }
}
