package com.skybooker.flight.mapper;

import com.skybooker.flight.entity.FlightCabin;
import com.skybooker.flight.vo.FlightCabinVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FlightCabinMapper {

    /** 按航班查舱位配置(含实时余座),按 FIRST→BUSINESS→ECONOMY 排序。 */
    List<FlightCabinVO> findByFlightId(@Param("flightId") Long flightId);

    void deleteByFlightId(@Param("flightId") Long flightId);

    /** 批量写入/覆盖(依赖 uk_flight_cabin 做 ON DUPLICATE KEY UPDATE)。 */
    void batchUpsert(@Param("cabins") List<FlightCabin> cabins);
}
