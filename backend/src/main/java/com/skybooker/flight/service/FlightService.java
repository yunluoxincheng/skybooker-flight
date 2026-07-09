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

@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightMapper flightMapper;

    public PageResponse<FlightVO> searchFlights(FlightSearchDTO dto) {
        int page = dto.getPage() != null && dto.getPage() > 0 ? dto.getPage() : 1;
        int size = dto.getSize() != null && dto.getSize() > 0 ? dto.getSize() : 10;
        int offset = (page - 1) * size;

        // cabinClass 白名单:排序时会拼入 ORDER BY(${}),必须校验防 SQL 注入
        if (dto.getCabinClass() != null && !FlightSort.isValidCabin(dto.getCabinClass())) {
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
                || dto.getIncludeSoldOut() != null
                || dto.getSort() != null;

        if (hasAdvanced) {
            FlightSort sort = FlightSort.fromParam(dto.getSort());
            // HTTP 入口对非法 sort 显式报 400 并列出合法枚举,不再静默回落默认排序。
            // AI 模块直接调 FlightSort.fromParam 自行处理回落,不经此校验。
            if (sort == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "不支持的排序参数，支持：DEFAULT、PRICE_ASC、DURATION_ASC、TIME_ASC、SEATS_DESC、PUNCTUAL_DESC");
            }
            String orderBy = sort.orderBy(dto.getCabinClass());

            List<FlightVO> records = flightMapper.searchFlightsAdvanced(
                    dto.getFlightNo(), dto.getDepartureCity(), dto.getArrivalCity(),
                    dto.getDepartureDate(), dto.getDepartureDateStart(), dto.getDepartureDateEnd(),
                    dto.getAirlineId(), dto.getMinPrice(), dto.getMaxPrice(),
                    dto.getDepartureTimeStart(), dto.getDepartureTimeEnd(), dto.getMaxDurationMinutes(),
                    dto.getDirectOnly(), dto.getStatus(), dto.getPassengerCount(), dto.getCabinClass(),
                    dto.getIncludeSoldOut(), orderBy, offset, size);
            long total = flightMapper.countFlightsAdvanced(
                    dto.getFlightNo(), dto.getDepartureCity(), dto.getArrivalCity(),
                    dto.getDepartureDate(), dto.getDepartureDateStart(), dto.getDepartureDateEnd(),
                    dto.getAirlineId(), dto.getMinPrice(), dto.getMaxPrice(),
                    dto.getDepartureTimeStart(), dto.getDepartureTimeEnd(), dto.getMaxDurationMinutes(),
                    dto.getDirectOnly(), dto.getStatus(), dto.getPassengerCount(), dto.getCabinClass(),
                    dto.getIncludeSoldOut());

            return new PageResponse<>(records, total, page, size);
        }

        List<FlightVO> records = flightMapper.searchFlights(
                dto.getFlightNo(), dto.getDepartureCity(), dto.getArrivalCity(),
                dto.getDepartureDate(), dto.getDepartureDateStart(), dto.getDepartureDateEnd(), offset, size);
        long total = flightMapper.countFlights(
                dto.getFlightNo(), dto.getDepartureCity(), dto.getArrivalCity(),
                dto.getDepartureDate(), dto.getDepartureDateStart(), dto.getDepartureDateEnd());

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
