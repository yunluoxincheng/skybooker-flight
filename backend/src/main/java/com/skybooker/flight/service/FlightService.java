package com.skybooker.flight.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.dto.FlightSearchDTO;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.vo.FlightSeatVO;
import com.skybooker.flight.vo.FlightVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightMapper flightMapper;

    public PageResponse<FlightVO> searchFlights(FlightSearchDTO dto) {
        int page = dto.getPage() != null && dto.getPage() > 0 ? dto.getPage() : 1;
        int size = dto.getSize() != null && dto.getSize() > 0 ? dto.getSize() : 10;
        int offset = (page - 1) * size;

        List<FlightVO> records = flightMapper.searchFlights(
                dto.getFlightNo(), dto.getDepartureCity(), dto.getArrivalCity(),
                dto.getDepartureDate(), offset, size);
        long total = flightMapper.countFlights(
                dto.getFlightNo(), dto.getDepartureCity(), dto.getArrivalCity(),
                dto.getDepartureDate());

        return new PageResponse<>(records, total, page, size);
    }

    public FlightVO getFlightDetail(Long id) {
        FlightVO flight = flightMapper.findPublishedFlightById(id);
        if (flight == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return flight;
    }

    public List<FlightSeatVO> getFlightSeats(Long flightId) {
        FlightVO flight = flightMapper.findPublishedFlightById(flightId);
        if (flight == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return flightMapper.findSeatsByFlightId(flightId);
    }
}
