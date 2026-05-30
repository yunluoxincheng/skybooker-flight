package com.skybooker.passenger.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.passenger.dto.PassengerDTO;
import com.skybooker.passenger.entity.Passenger;
import com.skybooker.passenger.mapper.PassengerMapper;
import com.skybooker.passenger.vo.PassengerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PassengerService {

    private final PassengerMapper passengerMapper;

    public List<PassengerVO> listMyPassengers() {
        Long userId = SecurityUtil.getCurrentUserId();
        return passengerMapper.findByUserId(userId);
    }

    public PassengerVO createPassenger(PassengerDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (passengerMapper.existsByIdCardNo(userId, dto.getIdCardNo(), null)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PASSENGER);
        }
        Passenger passenger = new Passenger();
        passenger.setUserId(userId);
        passenger.setName(dto.getName());
        passenger.setIdCardNo(dto.getIdCardNo());
        passenger.setPassengerType(dto.getPassengerType() != null ? dto.getPassengerType() : "ADULT");
        passenger.setPhone(dto.getPhone());
        passengerMapper.insert(passenger);
        return toVO(passenger);
    }

    public PassengerVO updatePassenger(Long id, PassengerDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        Passenger existing = passengerMapper.findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (passengerMapper.existsByIdCardNo(userId, dto.getIdCardNo(), id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PASSENGER);
        }
        existing.setName(dto.getName());
        existing.setIdCardNo(dto.getIdCardNo());
        existing.setPassengerType(dto.getPassengerType() != null ? dto.getPassengerType() : existing.getPassengerType());
        existing.setPhone(dto.getPhone());
        passengerMapper.update(existing);
        return toVO(existing);
    }

    public void deletePassenger(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        Passenger existing = passengerMapper.findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (passengerMapper.hasOrderHistory(id)) {
            throw new BusinessException(ErrorCode.PASSENGER_HAS_ORDERS);
        }
        passengerMapper.deleteById(id);
    }

    private PassengerVO toVO(Passenger p) {
        return new PassengerVO(p.getId(), p.getName(), p.getIdCardNo(), p.getPassengerType(), p.getPhone());
    }
}
