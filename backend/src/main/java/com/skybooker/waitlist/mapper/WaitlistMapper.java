package com.skybooker.waitlist.mapper;

import com.skybooker.waitlist.entity.WaitlistOrder;
import com.skybooker.waitlist.entity.WaitlistPassenger;
import com.skybooker.waitlist.vo.WaitlistVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WaitlistMapper {

    void insertWaitlistOrder(WaitlistOrder order);

    void batchInsertWaitlistPassengers(@Param("list") List<WaitlistPassenger> list);

    WaitlistVO findDetailById(@Param("id") Long id);

    List<WaitlistVO> findByUserId(@Param("userId") Long userId);

    int updateStatusCAS(@Param("id") Long id,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("newStatus") String newStatus);

    void updateStatus(@Param("id") Long id, @Param("status") String status);

    void updatePaidAt(@Param("id") Long id, @Param("paidAt") java.time.LocalDateTime paidAt);

    void updateTicketOrderId(@Param("id") Long id, @Param("ticketOrderId") Long ticketOrderId);

    void updateSkipReason(@Param("id") Long id, @Param("reason") String reason);

    void updateRefund(@Param("id") Long id,
                      @Param("refundAmount") java.math.BigDecimal refundAmount,
                      @Param("refundTime") java.time.LocalDateTime refundTime);

    List<WaitlistVO> findWaitingByFlightAndCabin(@Param("flightId") Long flightId,
                                                  @Param("cabinClass") String cabinClass);

    List<WaitlistOrder> findExpiredPending();

    List<WaitlistOrder> findUnfulfillableWaiting();
}
