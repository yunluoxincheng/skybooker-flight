package com.skybooker.itinerary.mapper;

import com.skybooker.itinerary.vo.ConnectingPairVO;
import com.skybooker.itinerary.entity.ConnectingItinerary;
import com.skybooker.admin.vo.ConnectingItinerarySummaryVO;
import com.skybooker.flight.vo.FlightVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import com.skybooker.itinerary.vo.FareCalendarVO;

@Mapper
public interface ItineraryMapper {
    List<ConnectingPairVO> findConnectingPairs(@Param("departureCities") List<String> departureCities,
                                                @Param("arrivalCities") List<String> arrivalCities,
                                                @Param("departureDate") LocalDate departureDate,
                                                @Param("passengerCount") int passengerCount,
                                                @Param("cabinClass") String cabinClass);

    List<ConnectingItinerarySummaryVO> findManagedSummaries(@Param("keyword") String keyword, @Param("schemeId") Long schemeId,
            @Param("segmentScope") String segmentScope, @Param("offset") int offset, @Param("size") int size);
    long countManaged(@Param("keyword") String keyword, @Param("schemeId") Long schemeId, @Param("segmentScope") String segmentScope);
    ConnectingItinerary findManagedById(@Param("id") Long id);
    ConnectingItinerary findManagedPair(@Param("firstFlightId") Long firstFlightId,
                                        @Param("secondFlightId") Long secondFlightId);
    void insertManaged(ConnectingItinerary itinerary);
    int updateManagedFlights(@Param("id") Long id, @Param("firstFlightId") Long firstFlightId,
                             @Param("secondFlightId") Long secondFlightId);
    int updateManagedStatus(@Param("id") Long id, @Param("publishStatus") String publishStatus);
    int deleteManaged(@Param("id") Long id);
    List<FlightVO> findFlightCandidates(@Param("keyword") String keyword,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        @Param("departureAirportId") Long departureAirportId,
                                        @Param("arrivalAirportId") Long arrivalAirportId,
                                        @Param("firstFlightId") Long firstFlightId,
                                        @Param("offset") int offset, @Param("size") int size);
    long countFlightCandidates(@Param("keyword") String keyword,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate,
                               @Param("departureAirportId") Long departureAirportId,
                               @Param("arrivalAirportId") Long arrivalAirportId,
                               @Param("firstFlightId") Long firstFlightId);
    List<FareCalendarVO> findFareCalendar(@Param("departureCities") List<String> departureCities,
                                          @Param("arrivalCities") List<String> arrivalCities,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);
}
