package com.skybooker.waitlist.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.passenger.entity.Passenger;
import com.skybooker.passenger.mapper.PassengerMapper;
import com.skybooker.waitlist.dto.CreateWaitlistDTO;
import com.skybooker.waitlist.entity.WaitlistOrder;
import com.skybooker.waitlist.entity.WaitlistPassenger;
import com.skybooker.waitlist.mapper.WaitlistMapper;
import com.skybooker.waitlist.vo.WaitlistVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistService {

    private final WaitlistMapper waitlistMapper;
    private final FlightMapper flightMapper;
    private final PassengerMapper passengerMapper;

    private static final BigDecimal AIRPORT_FEE_PER_PASSENGER = new BigDecimal("50.00");
    private static final BigDecimal FUEL_FEE_PER_PASSENGER = new BigDecimal("30.00");
    private static final BigDecimal SERVICE_FEE = BigDecimal.ZERO;
    private static final int WAITLIST_EXPIRY_MINUTES = 15;

    @Transactional
    public WaitlistVO createWaitlist(CreateWaitlistDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<Long> passengerIds = dto.getPassengerIds();
        int passengerCount = passengerIds.size();

        validateDuplicatePassengers(passengerIds);

        Flight flight = validateFlightSellability(dto.getFlightId());
        validatePassengerOwnership(passengerIds, userId);
        validateCabinInventory(dto.getFlightId(), dto.getCabinClass(), passengerCount);

        BigDecimal unitPrice = flightMapper.findMinPriceByFlightAndCabin(dto.getFlightId(), dto.getCabinClass());
        if (unitPrice == null) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }

        BigDecimal payAmount = unitPrice.multiply(BigDecimal.valueOf(passengerCount))
                .add(AIRPORT_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(passengerCount)))
                .add(FUEL_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(passengerCount)))
                .add(SERVICE_FEE);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusMinutes(WAITLIST_EXPIRY_MINUTES);

        WaitlistOrder order = new WaitlistOrder();
        order.setWaitlistNo(generateWaitlistNo());
        order.setUserId(userId);
        order.setFlightId(dto.getFlightId());
        order.setPassengerCount(passengerCount);
        order.setCabinClass(dto.getCabinClass());
        order.setPayAmount(payAmount);
        order.setStatus("PENDING_PAYMENT");
        order.setExpireTime(expireTime);
        waitlistMapper.insertWaitlistOrder(order);

        List<Passenger> passengers = new ArrayList<>();
        for (Long pid : passengerIds) {
            passengers.add(passengerMapper.findById(pid));
        }

        List<WaitlistPassenger> wlPassengers = new ArrayList<>();
        for (Passenger p : passengers) {
            WaitlistPassenger wp = new WaitlistPassenger();
            wp.setWaitlistId(order.getId());
            wp.setPassengerId(p.getId());
            wp.setPassengerName(p.getName());
            wp.setPassengerType(p.getPassengerType());
            wlPassengers.add(wp);
        }
        waitlistMapper.batchInsertWaitlistPassengers(wlPassengers);

        return waitlistMapper.findDetailById(order.getId());
    }

    public List<WaitlistVO> listMyWaitlists() {
        Long userId = SecurityUtil.getCurrentUserId();
        return waitlistMapper.findByUserId(userId);
    }

    public WaitlistVO getWaitlistDetail(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        WaitlistVO vo = waitlistMapper.findDetailById(id);
        if (vo == null || !vo.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAITLIST_NOT_FOUND);
        }
        return vo;
    }

    @Transactional
    public WaitlistVO payWaitlist(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        WaitlistVO vo = waitlistMapper.findDetailById(id);
        if (vo == null || !vo.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAITLIST_NOT_FOUND);
        }

        if ("WAITING".equals(vo.getStatus())) {
            return vo;
        }
        if ("SUCCESS".equals(vo.getStatus())) {
            return vo;
        }
        if (!"PENDING_PAYMENT".equals(vo.getStatus())) {
            throw new BusinessException(ErrorCode.WAITLIST_STATE_INVALID);
        }
        if (vo.getExpireTime() != null && vo.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.WAITLIST_STATE_INVALID);
        }

        int cas = waitlistMapper.updateStatusCAS(id, "PENDING_PAYMENT", "WAITING");
        if (cas == 0) {
            return waitlistMapper.findDetailById(id);
        }
        waitlistMapper.updatePaidAt(id, LocalDateTime.now());

        return waitlistMapper.findDetailById(id);
    }

    @Transactional
    public WaitlistVO cancelWaitlist(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        WaitlistVO vo = waitlistMapper.findDetailById(id);
        if (vo == null || !vo.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAITLIST_NOT_FOUND);
        }

        if ("CANCELLED".equals(vo.getStatus()) || "REFUNDED".equals(vo.getStatus())) {
            return vo;
        }
        if ("SUCCESS".equals(vo.getStatus())) {
            throw new BusinessException(ErrorCode.WAITLIST_STATE_INVALID);
        }

        if ("PENDING_PAYMENT".equals(vo.getStatus())) {
            int cas = waitlistMapper.updateStatusCAS(id, "PENDING_PAYMENT", "CANCELLED");
            if (cas == 0) {
                return waitlistMapper.findDetailById(id);
            }
            return waitlistMapper.findDetailById(id);
        }

        if ("WAITING".equals(vo.getStatus())) {
            int cas = waitlistMapper.updateStatusCAS(id, "WAITING", "CANCELLED");
            if (cas == 0) {
                return waitlistMapper.findDetailById(id);
            }
            waitlistMapper.updateStatus(id, "REFUNDED");
            waitlistMapper.updateRefund(id, vo.getPayAmount(), LocalDateTime.now());
            return waitlistMapper.findDetailById(id);
        }

        throw new BusinessException(ErrorCode.WAITLIST_STATE_INVALID);
    }

    @Transactional
    public void cleanupExpiredPending() {
        List<WaitlistOrder> expired = waitlistMapper.findExpiredPending();
        for (WaitlistOrder order : expired) {
            try {
                waitlistMapper.updateStatusCAS(order.getId(), "PENDING_PAYMENT", "EXPIRED");
            } catch (Exception e) {
                log.warn("Failed to cleanup expired waitlist {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void cleanupUnfulfillableWaiting() {
        List<WaitlistOrder> unfulfillable = waitlistMapper.findUnfulfillableWaiting();
        for (WaitlistOrder order : unfulfillable) {
            try {
                int cas = waitlistMapper.updateStatusCAS(order.getId(), "WAITING", "FAILED");
                if (cas > 0) {
                    waitlistMapper.updateStatus(order.getId(), "REFUNDED");
                    waitlistMapper.updateRefund(order.getId(), order.getPayAmount(), LocalDateTime.now());
                }
            } catch (Exception e) {
                log.warn("Failed to cleanup unfulfillable waitlist {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    private void validateDuplicatePassengers(List<Long> passengerIds) {
        Set<Long> seen = new HashSet<>();
        for (Long pid : passengerIds) {
            if (!seen.add(pid)) {
                throw new BusinessException(ErrorCode.DUPLICATE_WAITLIST_PASSENGER);
            }
        }
    }

    private Flight validateFlightSellability(Long flightId) {
        Flight flight = flightMapper.findById(flightId);
        if (flight == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PUBLISHED".equals(flight.getPublishStatus())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
        if (flight.getDepartureTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
        if (!"ON_TIME".equals(flight.getStatus()) && !"DELAYED".equals(flight.getStatus())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
        return flight;
    }

    private void validatePassengerOwnership(List<Long> passengerIds, Long userId) {
        for (Long pid : passengerIds) {
            Passenger p = passengerMapper.findById(pid);
            if (p == null || !p.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
        }
    }

    private void validateCabinInventory(Long flightId, String cabinClass, int passengerCount) {
        int available = flightMapper.countAvailableSeatsByFlightAndCabin(flightId, cabinClass);
        if (available >= passengerCount) {
            throw new BusinessException(ErrorCode.WAITLIST_NOT_NEEDED);
        }
    }

    private String generateWaitlistNo() {
        return "WL" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
