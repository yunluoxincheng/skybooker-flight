package com.skybooker.order.mapper;

import com.skybooker.order.entity.OrderPassenger;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    void insertOrder(TicketOrder order);

    void insertOrderPassenger(OrderPassenger op);

    void batchInsertOrderPassengers(@Param("list") List<OrderPassenger> list);

    TicketOrder findById(@Param("id") Long id);

    List<OrderVO> findByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    long countByUserId(@Param("userId") Long userId);

    OrderVO findDetailById(@Param("id") Long id);

    void updateOrderStatus(@Param("id") Long id, @Param("status") String status);

    void updatePayTime(@Param("id") Long id, @Param("payTime") java.time.LocalDateTime payTime);

    List<TicketOrder> findExpiredPendingOrders();

    List<OrderVO> findAllOrders(@Param("offset") int offset, @Param("size") int size);

    long countAllOrders();
}
