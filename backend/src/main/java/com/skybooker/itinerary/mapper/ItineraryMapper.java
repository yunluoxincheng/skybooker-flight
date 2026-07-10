package com.skybooker.itinerary.mapper;

import com.skybooker.itinerary.vo.ConnectingPairVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ItineraryMapper {
    List<ConnectingPairVO> findConnectingPairs(@Param("departureCity") String departureCity,
                                                @Param("arrivalCity") String arrivalCity,
                                                @Param("departureDate") LocalDate departureDate,
                                                @Param("passengerCount") int passengerCount,
                                                @Param("cabinClass") String cabinClass);
}
