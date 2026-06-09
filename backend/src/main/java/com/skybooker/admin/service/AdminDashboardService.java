package com.skybooker.admin.service;

import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.admin.vo.DashboardSummaryVO;
import com.skybooker.admin.vo.HotRouteVO;
import com.skybooker.admin.vo.OrderStatusDistributionVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int DEFAULT_HOT_ROUTE_LIMIT = 10;
    private static final int MAX_HOT_ROUTE_LIMIT = 20;
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");

    private final AdminMapper adminMapper;

    public DashboardSummaryVO getSummary() {
        DashboardSummaryVO summary = adminMapper.selectDashboardSummary();
        summary.setGrossIssuedOrderRevenue(zeroIfNull(summary.getGrossIssuedOrderRevenue()));
        summary.setTicketRefundAmount(zeroIfNull(summary.getTicketRefundAmount()));
        summary.setWaitlistRefundAmount(zeroIfNull(summary.getWaitlistRefundAmount()));
        summary.setTotalRefundAmount(summary.getTicketRefundAmount().add(summary.getWaitlistRefundAmount()));
        return summary;
    }

    public List<HotRouteVO> listHotRoutes(Integer limit) {
        int resolvedLimit = resolveHotRouteLimit(limit);
        List<HotRouteVO> routes = adminMapper.selectHotRoutes(resolvedLimit);
        routes.forEach(route -> route.setRevenue(zeroIfNull(route.getRevenue())));
        return routes;
    }

    public List<OrderStatusDistributionVO> listOrderStatusDistribution() {
        return adminMapper.selectOrderStatusDistribution();
    }

    private int resolveHotRouteLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_HOT_ROUTE_LIMIT;
        }
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return Math.min(limit, MAX_HOT_ROUTE_LIMIT);
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? ZERO_AMOUNT : amount;
    }
}
