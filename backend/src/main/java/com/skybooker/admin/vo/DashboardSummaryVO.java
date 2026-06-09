package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryVO {
    private Long totalUsers;
    private Long totalFlights;
    private Long totalTicketOrders;
    private Long issuedTicketOrders;
    private Long refundedTicketOrders;
    private Long pendingPaymentTicketOrders;
    private Long totalWaitlistOrders;
    private Long waitingWaitlistOrders;
    private BigDecimal grossIssuedOrderRevenue;
    private BigDecimal ticketRefundAmount;
    private BigDecimal waitlistRefundAmount;
    private BigDecimal totalRefundAmount;
}
