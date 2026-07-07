package com.skybooker.admin.service;

import com.skybooker.admin.dto.AirlineDTO;
import com.skybooker.admin.dto.AirlineUpdateDTO;
import com.skybooker.admin.entity.Airline;
import com.skybooker.admin.mapper.AirlineMapper;
import com.skybooker.admin.vo.AirlineVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAirlineService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final AirlineMapper airlineMapper;

    public PageResponse<AirlineVO> listAirlines(String keyword, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<AirlineVO> records = airlineMapper.search(keyword, status, offset, size);
        long total = airlineMapper.count(keyword, status);
        return new PageResponse<>(records, total, page, size);
    }

    @Transactional
    public AirlineVO createAirline(AirlineDTO dto) {
        // 预检给出友好错误码；表本身的 UNIQUE(code) 兜底并发场景
        if (airlineMapper.existsByCode(dto.getCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_AIRLINE_CODE);
        }
        Airline airline = new Airline();
        airline.setCode(dto.getCode());
        airline.setName(dto.getName());
        airline.setLogoUrl(dto.getLogoUrl());
        airline.setStatus(STATUS_ENABLED);
        airlineMapper.insert(airline);
        return airlineMapper.findById(airline.getId());
    }

    @Transactional
    public AirlineVO updateAirline(Long id, AirlineUpdateDTO dto) {
        if (airlineMapper.findById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // code 是稳定标识，创建后不可改；仅更新 name / logoUrl
        Airline update = new Airline();
        update.setId(id);
        update.setName(dto.getName());
        update.setLogoUrl(dto.getLogoUrl());
        airlineMapper.update(update);
        return airlineMapper.findById(id);
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
        if (airlineMapper.findById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        airlineMapper.updateStatus(id, status);
    }
}
