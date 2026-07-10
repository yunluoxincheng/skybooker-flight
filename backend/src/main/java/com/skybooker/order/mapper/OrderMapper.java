package com.skybooker.order.mapper;

import com.skybooker.admin.dto.AdminOrderQueryDTO;
import com.skybooker.order.entity.OrderPassenger;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.entity.TicketOrderSegment;
import com.skybooker.order.entity.OrderSegmentPassenger;
import com.skybooker.order.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    void insertOrder(TicketOrder order);

    void insertOrderPassenger(OrderPassenger op);

    void batchInsertOrderPassengers(@Param("list") List<OrderPassenger> list);
    void insertOrderSegment(TicketOrderSegment segment);
    void batchInsertSegmentPassengers(@Param("list") List<OrderSegmentPassenger> list);
    List<TicketOrderSegment> findSegmentsByOrderId(@Param("orderId") Long orderId);
    List<OrderSegmentPassenger> findSegmentPassengers(@Param("segmentId") Long segmentId);
    TicketOrder findByUserAndClientRequestId(@Param("userId") Long userId, @Param("clientRequestId") String clientRequestId);
    Long lockUserForIdempotency(@Param("userId") Long userId);
    void deleteSegmentPassengersByOrderId(@Param("orderId") Long orderId);
    void deleteSegmentsByOrderId(@Param("orderId") Long orderId);

    TicketOrder findById(@Param("id") Long id);
    TicketOrder findByIdForUpdate(@Param("id") Long id);

    List<OrderVO> findByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    long countByUserId(@Param("userId") Long userId);

    OrderVO findDetailById(@Param("id") Long id);

    void updateOrderStatus(@Param("id") Long id, @Param("status") String status);

    int updateOrderStatusCAS(@Param("id") Long id,
                             @Param("expectedStatus") String expectedStatus,
                             @Param("newStatus") String newStatus);

    void updatePayTime(@Param("id") Long id, @Param("payTime") java.time.LocalDateTime payTime);

    List<TicketOrder> findExpiredPendingOrders();

    List<OrderVO> findAllOrders(@Param("offset") int offset, @Param("size") int size);

    long countAllOrders();

    List<OrderVO> searchOrdersAdmin(@Param("query") AdminOrderQueryDTO query,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    long countOrdersAdmin(@Param("query") AdminOrderQueryDTO query);

    void updateOrderFlightAndAmounts(@Param("id") Long id,
                                     @Param("flightId") Long flightId,
                                     @Param("ticketAmount") java.math.BigDecimal ticketAmount,
                                     @Param("airportFee") java.math.BigDecimal airportFee,
                                     @Param("fuelFee") java.math.BigDecimal fuelFee,
                                     @Param("serviceFee") java.math.BigDecimal serviceFee,
                                     @Param("totalAmount") java.math.BigDecimal totalAmount);

    void updateOrderPassengerSeat(@Param("id") Long id,
                                  @Param("seatId") Long seatId,
                                  @Param("seatNo") String seatNo,
                                  @Param("ticketPrice") java.math.BigDecimal ticketPrice);

    int updateAdminNote(@Param("id") Long id, @Param("adminNote") String adminNote);

    List<OrderPassenger> findPassengersByOrderId(@Param("orderId") Long orderId);
}
