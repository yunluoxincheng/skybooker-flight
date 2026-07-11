package com.skybooker.itinerary.mapper;

import com.skybooker.itinerary.vo.ConnectingPairVO;
import com.skybooker.itinerary.entity.ConnectingItinerary;
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

    List<ConnectingItinerary> findAllManaged();
    ConnectingItinerary findManagedById(@Param("id") Long id);
    ConnectingItinerary findManagedPair(@Param("firstFlightId") Long firstFlightId,
                                        @Param("secondFlightId") Long secondFlightId);
    void insertManaged(ConnectingItinerary itinerary);
    int updateManagedFlights(@Param("id") Long id, @Param("firstFlightId") Long firstFlightId,
                             @Param("secondFlightId") Long secondFlightId);
    int updateManagedStatus(@Param("id") Long id, @Param("publishStatus") String publishStatus);
}
