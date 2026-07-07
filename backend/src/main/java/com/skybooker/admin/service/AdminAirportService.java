package com.skybooker.admin.service;

import com.skybooker.admin.dto.AirportDTO;
import com.skybooker.admin.entity.Airport;
import com.skybooker.admin.mapper.AirportMapper;
import com.skybooker.admin.vo.AirportVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAirportService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final AirportMapper airportMapper;

    public PageResponse<AirportVO> listAirports(String keyword, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<AirportVO> records = airportMapper.search(keyword, status, offset, size);
        long total = airportMapper.count(keyword, status);
        return new PageResponse<>(records, total, page, size);
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
    public AirportVO updateAirport(Long id, AirportDTO dto) {
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

    private void setStatus(Long id, String status) {
        if (airportMapper.findById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        airportMapper.updateStatus(id, status);
    }
}
