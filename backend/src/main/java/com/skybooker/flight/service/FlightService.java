package com.skybooker.flight.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.dto.FlightSearchDTO;
import com.skybooker.flight.enums.FlightSort;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.vo.FlightSeatVO;
import com.skybooker.flight.vo.FlightVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlightService {

    private static final String DEFAULT_ORDER = "f.departure_time";
    private static final Set<String> VALID_CABINS = Set.of("ECONOMY", "BUSINESS", "FIRST");

    private final FlightMapper flightMapper;

    public PageResponse<FlightVO> searchFlights(FlightSearchDTO dto) {
        int page = dto.getPage() != null && dto.getPage() > 0 ? dto.getPage() : 1;
        int size = dto.getSize() != null && dto.getSize() > 0 ? dto.getSize() : 10;
        int offset = (page - 1) * size;

        // cabinClass 白名单:排序时会拼入 ORDER BY(${}),必须校验防 SQL 注入
        if (dto.getCabinClass() != null && !VALID_CABINS.contains(dto.getCabinClass())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        boolean hasAdvanced = dto.getAirlineId() != null
                || dto.getMinPrice() != null || dto.getMaxPrice() != null
                || dto.getDepartureTimeStart() != null || dto.getDepartureTimeEnd() != null
                || dto.getMaxDurationMinutes() != null
                || dto.getDirectOnly() != null
                || dto.getStatus() != null
                || dto.getPassengerCount() != null
                || dto.getCabinClass() != null
                || dto.getSort() != null;

        if (hasAdvanced) {
            FlightSort sort = FlightSort.fromParam(dto.getSort());
            String orderBy = (sort != null) ? sortToOrderBy(sort, dto.getCabinClass()) : DEFAULT_ORDER;

            List<FlightVO> records = flightMapper.searchFlightsAdvanced(
                    dto.getFlightNo(), dto.getDepartureCity(), dto.getArrivalCity(),
                    dto.getDepartureDate(), dto.getAirlineId(), dto.getMinPrice(), dto.getMaxPrice(),
                    dto.getDepartureTimeStart(), dto.getDepartureTimeEnd(), dto.getMaxDurationMinutes(),
                    dto.getDirectOnly(), dto.getStatus(), dto.getPassengerCount(), dto.getCabinClass(),
                    orderBy, offset, size);
            long total = flightMapper.countFlightsAdvanced(
                    dto.getFlightNo(), dto.getDepartureCity(), dto.getArrivalCity(),
                    dto.getDepartureDate(), dto.getAirlineId(), dto.getMinPrice(), dto.getMaxPrice(),
                    dto.getDepartureTimeStart(), dto.getDepartureTimeEnd(), dto.getMaxDurationMinutes(),
                    dto.getDirectOnly(), dto.getStatus(), dto.getPassengerCount(), dto.getCabinClass());

            return new PageResponse<>(records, total, page, size);
        }

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

    private String sortToOrderBy(FlightSort sort, String cabinClass) {
        // cabinClass 已在入口白名单校验,拼入 ORDER BY 安全
        boolean hasCabin = cabinClass != null && VALID_CABINS.contains(cabinClass);
        return switch (sort) {
            case PRICE_ASC -> hasCabin
                    ? "(SELECT MIN(fc.price) FROM flight_cabin fc WHERE fc.flight_id = f.id AND fc.cabin_class = '" + cabinClass + "') ASC, f.departure_time ASC"
                    : "f.base_price ASC, f.departure_time ASC";
            case DURATION_ASC -> "f.duration_minutes ASC, f.departure_time ASC";
            case TIME_ASC -> "f.departure_time ASC";
            case SEATS_DESC -> "f.remaining_seats DESC, f.departure_time ASC";
            case PUNCTUAL_DESC -> "f.punctuality_rate DESC, f.departure_time ASC";
            default -> DEFAULT_ORDER;
        };
    }
}
