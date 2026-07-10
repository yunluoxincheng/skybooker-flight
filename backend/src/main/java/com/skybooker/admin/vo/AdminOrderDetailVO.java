package com.skybooker.admin.vo;

import com.skybooker.change.entity.ChangeRecord;
import com.skybooker.change.entity.ConnectingChangeRecord;
import com.skybooker.order.vo.OrderVO;
import com.skybooker.refund.entity.RefundRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderDetailVO {
    private Long id;
    private String orderNo;
    private Long flightId;
    private String journeyType;
    private String flightNo;
    private String airlineName;
    private String departureCity;
    private String arrivalCity;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Long userId;
    private String userEmail;
    private String userNickname;
    private String status;
    private BigDecimal ticketAmount;
    private BigDecimal airportFee;
    private BigDecimal fuelFee;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;
    private String adminNote;
    private LocalDateTime createdAt;
    private List<OrderVO.OrderPassengerVO> passengers;
    private List<OrderVO.OrderSegmentVO> segments;
    private List<RefundRecord> refunds;
    private List<ChangeRecord> changes;
    private List<ConnectingChangeRecord> connectingChanges;
    private List<AdminOrderTimelineItemVO> timeline;

    public static AdminOrderDetailVO from(OrderVO order,
                                          List<RefundRecord> refunds,
                                          List<ChangeRecord> changes,
                                          List<ConnectingChangeRecord> connectingChanges,
                                          List<AdminOrderTimelineItemVO> timeline) {
        return new AdminOrderDetailVO(
                order.getId(),
                order.getOrderNo(),
                order.getFlightId(),
                order.getJourneyType(),
                order.getFlightNo(),
                order.getAirlineName(),
                order.getDepartureCity(),
                order.getArrivalCity(),
                order.getDepartureTime(),
                order.getArrivalTime(),
                order.getUserId(),
                order.getUserEmail(),
                order.getUserNickname(),
                order.getStatus(),
                order.getTicketAmount(),
                order.getAirportFee(),
                order.getFuelFee(),
                order.getServiceFee(),
                order.getTotalAmount(),
                order.getPayTime(),
                order.getExpireTime(),
                order.getAdminNote(),
                order.getCreatedAt(),
                order.getPassengers(),
                order.getSegments(),
                refunds,
                changes,
                connectingChanges,
                timeline
        );
    }
}
