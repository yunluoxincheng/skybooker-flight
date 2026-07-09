package com.skybooker.admin.service;

import com.skybooker.admin.dto.AirportDTO;
import com.skybooker.admin.dto.AirportUpdateDTO;
import com.skybooker.admin.dto.AdminKeywordStatusQueryDTO;
import com.skybooker.admin.entity.Airport;
import com.skybooker.admin.mapper.AirportMapper;
import com.skybooker.admin.support.AdminListQuerySupport;
import com.skybooker.admin.vo.AirportVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.mapper.FlightMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAirportService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final Set<String> VALID_STATUSES = Set.of(STATUS_ENABLED, STATUS_DISABLED);

    private final AirportMapper airportMapper;
    private final FlightMapper flightMapper;
    private final AdminOperationLogService operationLogService;

    public PageResponse<AirportVO> listAirports(AdminKeywordStatusQueryDTO query) {
        AdminListQuerySupport.normalize(query);
        validateEnum(query.getStatus());
        int offset = AdminListQuerySupport.offset(query);
        List<AirportVO> records = airportMapper.search(query, offset, query.getSize());
        long total = airportMapper.count(query);
        return new PageResponse<>(records, total, query.getPage(), query.getSize());
    }

    @Transactional
    public AirportVO createAirport(AirportDTO dto) {
        // 预检给出友好错误码；表本身的 UNIQUE(code) 兜底并发场景
        if (airportMapper.existsByCode(dto.getCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_AIRPORT_CODE);
        }
        Airport airport = new Airport();
        airport.setCode(dto.getCode());
        airport.setName(dto.getName());
        airport.setCity(dto.getCity());
        airport.setProvince(dto.getProvince());
        airport.setStatus(STATUS_ENABLED);
        airportMapper.insert(airport);
        return airportMapper.findById(airport.getId());
    }

    @Transactional
    public AirportVO updateAirport(Long id, AirportUpdateDTO dto) {
        if (airportMapper.findById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // code 是稳定标识，创建后不可改；仅更新 name / city / province
        Airport update = new Airport();
        update.setId(id);
        update.setName(dto.getName());
        update.setCity(dto.getCity());
        update.setProvince(dto.getProvince());
        airportMapper.update(update);
        return airportMapper.findById(id);
    }

    @Transactional
    public void disable(Long id) {
        setStatus(id, STATUS_DISABLED);
    }

    @Transactional
    public void enable(Long id) {
        setStatus(id, STATUS_ENABLED);
    }

    /**
     * 物理删除机场。有出发/到达关联航班则阻断并引导管理员改用 {@link #disable}；
     * 应用层先拦给出友好错误，并发兜底由 {@code flight.departure_airport_id / arrival_airport_id}
     * 的 FK RESTRICT 保证。
     */
    @Transactional
    public void delete(Long id) {
        if (airportMapper.findById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (flightMapper.countFlightsByAirportId(id) > 0) {
            throw new BusinessException(ErrorCode.AIRPORT_IN_USE);
        }
        airportMapper.deleteById(id);
        operationLogService.log(operationLogService.currentAdminId(),
                AdminOperationLogService.TARGET_AIRPORT, id,
                AdminOperationLogService.ACTION_AIRPORT_DELETE, null);
    }

    private void setStatus(Long id, String status) {
        if (airportMapper.findById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        airportMapper.updateStatus(id, status);
    }

    private void validateEnum(String status) {
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
