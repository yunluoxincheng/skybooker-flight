package com.skybooker.admin.service;

import com.skybooker.admin.mapper.AdminReportMapper;
import com.skybooker.admin.vo.*;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final int MAX_DATE_RANGE_DAYS = 366;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");

    private final AdminReportMapper adminReportMapper;

    public List<SalesTrendVO> getSalesTrend(LocalDate startDate, LocalDate endDate, String granularity) {
        validateDateRange(startDate, endDate);
        validateGranularity(granularity);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<SalesTrendVO> rows;
        if ("DAY".equals(granularity)) {
            rows = adminReportMapper.selectSalesTrendByDay(startDateTime, endDateTime);
        } else {
            rows = adminReportMapper.selectSalesTrendByMonth(startDateTime, endDateTime);
        }

        Map<String, SalesTrendVO> byPeriod = rows.stream()
                .collect(Collectors.toMap(SalesTrendVO::getPeriod, r -> r));

        return fillTrendPeriods(startDate, endDate, granularity, byPeriod,
                (period) -> new SalesTrendVO(period, 0L, 0L, ZERO_AMOUNT));
    }

    public List<RoutePerformanceVO> getRoutePerformance(LocalDate startDate, LocalDate endDate,
                                                         Long airlineId, String departureCity,
                                                         String arrivalCity, Integer limit) {
        validateDateRange(startDate, endDate);
        int resolvedLimit = resolveLimit(limit);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<RoutePerformanceVO> rows = adminReportMapper.selectRoutePerformance(
                startDateTime, endDateTime, airlineId, departureCity, arrivalCity, resolvedLimit);

        rows.forEach(this::normalizeRoutePerformance);
        return rows;
    }

    public List<FlightLoadFactorVO> getFlightLoadFactor(LocalDate startDate, LocalDate endDate,
                                                          Long airlineId, String departureCity,
                                                          String arrivalCity, Integer limit) {
        validateDateRange(startDate, endDate);
        int resolvedLimit = resolveLimit(limit);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        return adminReportMapper.selectFlightLoadFactor(
                startDateTime, endDateTime, airlineId, departureCity, arrivalCity, resolvedLimit);
    }

    public List<RefundTrendVO> getRefundTrend(LocalDate startDate, LocalDate endDate, String granularity) {
        validateDateRange(startDate, endDate);
        validateGranularity(granularity);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<RefundTrendVO> rows;
        if ("DAY".equals(granularity)) {
            rows = adminReportMapper.selectRefundTrendByDay(startDateTime, endDateTime);
        } else {
            rows = adminReportMapper.selectRefundTrendByMonth(startDateTime, endDateTime);
        }

        Map<String, RefundTrendVO> byPeriod = rows.stream()
                .collect(Collectors.toMap(RefundTrendVO::getPeriod, r -> r));

        return fillTrendPeriods(startDate, endDate, granularity, byPeriod,
                (period) -> new RefundTrendVO(period, 0L, ZERO_AMOUNT));
    }

    public WaitlistPerformanceVO getWaitlistPerformance(LocalDate startDate, LocalDate endDate,
                                                         Long airlineId, String departureCity,
                                                         String arrivalCity) {
        validateDateRange(startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        WaitlistPerformanceVO result = adminReportMapper.selectWaitlistPerformance(
                startDateTime, endDateTime, airlineId, departureCity, arrivalCity);

        if (result == null || result.getSubmittedCount() == null) {
            result = new WaitlistPerformanceVO(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, ZERO_AMOUNT, ZERO_AMOUNT);
        }
        normalizeWaitlistPerformance(result);
        return result;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        long inclusiveDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (inclusiveDays > MAX_DATE_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateGranularity(String granularity) {
        if (granularity == null || (!"DAY".equals(granularity) && !"MONTH".equals(granularity))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        if (limit <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return Math.min(limit, MAX_LIMIT);
    }

    private <T> List<T> fillTrendPeriods(LocalDate startDate, LocalDate endDate, String granularity,
                                          Map<String, T> existing, java.util.function.Function<String, T> zeroFactory) {
        List<T> result = new ArrayList<>();
        if ("DAY".equals(granularity)) {
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                String key = current.format(DateTimeFormatter.ISO_LOCAL_DATE);
                T row = existing.getOrDefault(key, zeroFactory.apply(key));
                result.add(row);
                current = current.plusDays(1);
            }
        } else {
            YearMonth endMonth = YearMonth.from(endDate);
            YearMonth current = YearMonth.from(startDate);
            while (!current.isAfter(endMonth)) {
                String key = current.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                T row = existing.getOrDefault(key, zeroFactory.apply(key));
                result.add(row);
                current = current.plusMonths(1);
            }
        }
        return result;
    }

    private void normalizeRoutePerformance(RoutePerformanceVO vo) {
        vo.setRevenue(zeroIfNull(vo.getRevenue()));
        vo.setRefundAmount(zeroIfNull(vo.getRefundAmount()));
        vo.setNetRevenue(zeroIfNull(vo.getNetRevenue()));
    }

    private void normalizeWaitlistPerformance(WaitlistPerformanceVO vo) {
        vo.setSubmittedCount(zeroIfNull(vo.getSubmittedCount()));
        vo.setPendingPaymentCount(zeroIfNull(vo.getPendingPaymentCount()));
        vo.setWaitingCount(zeroIfNull(vo.getWaitingCount()));
        vo.setSuccessCount(zeroIfNull(vo.getSuccessCount()));
        vo.setFailedCount(zeroIfNull(vo.getFailedCount()));
        vo.setCancelledCount(zeroIfNull(vo.getCancelledCount()));
        vo.setRefundedCount(zeroIfNull(vo.getRefundedCount()));
        vo.setExpiredCount(zeroIfNull(vo.getExpiredCount()));
        vo.setPayAmount(zeroIfNull(vo.getPayAmount()));
        vo.setRefundAmount(zeroIfNull(vo.getRefundAmount()));
    }

    private Long zeroIfNull(Long count) {
        return count == null ? 0L : count;
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? ZERO_AMOUNT : amount;
    }
}
