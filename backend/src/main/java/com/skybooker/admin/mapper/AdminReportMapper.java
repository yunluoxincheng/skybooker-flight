package com.skybooker.admin.mapper;

import com.skybooker.admin.vo.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminReportMapper {

    List<SalesTrendVO> selectSalesTrendByDay(@Param("startDateTime") LocalDateTime startDateTime,
                                             @Param("endDateTime") LocalDateTime endDateTime);

    List<SalesTrendVO> selectSalesTrendByMonth(@Param("startDateTime") LocalDateTime startDateTime,
                                               @Param("endDateTime") LocalDateTime endDateTime);

    List<RoutePerformanceVO> selectRoutePerformance(@Param("startDateTime") LocalDateTime startDateTime,
                                                    @Param("endDateTime") LocalDateTime endDateTime,
                                                    @Param("airlineId") Long airlineId,
                                                    @Param("departureCity") String departureCity,
                                                    @Param("arrivalCity") String arrivalCity,
                                                    @Param("limit") int limit);

    List<FlightLoadFactorVO> selectFlightLoadFactor(@Param("startDateTime") LocalDateTime startDateTime,
                                                    @Param("endDateTime") LocalDateTime endDateTime,
                                                    @Param("airlineId") Long airlineId,
                                                    @Param("departureCity") String departureCity,
                                                    @Param("arrivalCity") String arrivalCity,
                                                    @Param("limit") int limit);

    List<RefundTrendVO> selectRefundTrendByDay(@Param("startDateTime") LocalDateTime startDateTime,
                                               @Param("endDateTime") LocalDateTime endDateTime);

    List<RefundTrendVO> selectRefundTrendByMonth(@Param("startDateTime") LocalDateTime startDateTime,
                                                 @Param("endDateTime") LocalDateTime endDateTime);

    WaitlistPerformanceVO selectWaitlistPerformance(@Param("startDateTime") LocalDateTime startDateTime,
                                                    @Param("endDateTime") LocalDateTime endDateTime,
                                                    @Param("airlineId") Long airlineId,
                                                    @Param("departureCity") String departureCity,
                                                    @Param("arrivalCity") String arrivalCity);
}
