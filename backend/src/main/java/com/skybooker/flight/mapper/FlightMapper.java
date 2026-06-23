package com.skybooker.flight.mapper;

import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.entity.FlightSeat;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.flight.vo.FlightSeatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface FlightMapper {

    List<FlightVO> searchFlights(@Param("flightNo") String flightNo,
                                 @Param("departureCity") String departureCity,
                                 @Param("arrivalCity") String arrivalCity,
                                 @Param("departureDate") LocalDate departureDate,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countFlights(@Param("flightNo") String flightNo,
                      @Param("departureCity") String departureCity,
                      @Param("arrivalCity") String arrivalCity,
                      @Param("departureDate") LocalDate departureDate);

    List<FlightVO> searchFlightsAdvanced(@Param("flightNo") String flightNo,
                                          @Param("departureCity") String departureCity,
                                          @Param("arrivalCity") String arrivalCity,
                                          @Param("departureDate") LocalDate departureDate,
                                          @Param("airlineId") Long airlineId,
                                          @Param("minPrice") BigDecimal minPrice,
                                          @Param("maxPrice") BigDecimal maxPrice,
                                          @Param("departureTimeStart") LocalTime departureTimeStart,
                                          @Param("departureTimeEnd") LocalTime departureTimeEnd,
                                          @Param("maxDurationMinutes") Integer maxDurationMinutes,
                                          @Param("directOnly") Boolean directOnly,
                                          @Param("status") String status,
                                          @Param("passengerCount") Integer passengerCount,
                                          @Param("cabinClass") String cabinClass,
                                          @Param("orderBy") String orderBy,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    long countFlightsAdvanced(@Param("flightNo") String flightNo,
                               @Param("departureCity") String departureCity,
                               @Param("arrivalCity") String arrivalCity,
                               @Param("departureDate") LocalDate departureDate,
                               @Param("airlineId") Long airlineId,
                               @Param("minPrice") BigDecimal minPrice,
                               @Param("maxPrice") BigDecimal maxPrice,
                               @Param("departureTimeStart") LocalTime departureTimeStart,
                               @Param("departureTimeEnd") LocalTime departureTimeEnd,
                               @Param("maxDurationMinutes") Integer maxDurationMinutes,
                               @Param("directOnly") Boolean directOnly,
                               @Param("status") String status,
                               @Param("passengerCount") Integer passengerCount,
                               @Param("cabinClass") String cabinClass);

    List<FlightVO> searchRecommendationFlights(@Param("departureCity") String departureCity,
                                                @Param("arrivalCity") String arrivalCity,
                                                @Param("departureDate") LocalDate departureDate,
                                                @Param("airlineId") Long airlineId,
                                                @Param("minPrice") BigDecimal minPrice,
                                                @Param("maxPrice") BigDecimal maxPrice,
                                                @Param("departureTimeStart") LocalTime departureTimeStart,
                                                @Param("departureTimeEnd") LocalTime departureTimeEnd,
                                                @Param("maxDurationMinutes") Integer maxDurationMinutes,
                                                @Param("directOnly") Boolean directOnly,
                                                @Param("passengerCount") Integer passengerCount,
                                                @Param("cabinClass") String cabinClass,
                                                @Param("orderBy") String orderBy,
                                                @Param("limit") int limit);

    FlightVO findPublishedFlightById(@Param("id") Long id);

    List<FlightSeatVO> findSeatsByFlightId(@Param("flightId") Long flightId);

    Flight findById(@Param("id") Long id);

    FlightSeat findSeatById(@Param("id") Long id);

    void insertFlight(Flight flight);

    void updateFlight(Flight flight);

    void insertFlightSeat(FlightSeat seat);

    void batchInsertFlightSeats(@Param("seats") List<FlightSeat> seats);

    boolean existsSeatsByFlightId(@Param("flightId") Long flightId);

    boolean existsOrdersByFlightId(@Param("flightId") Long flightId);

    int lockSeats(@Param("seatIds") List<Long> seatIds,
                  @Param("orderId") Long orderId,
                  @Param("lockExpireTime") java.time.LocalDateTime lockExpireTime);

    int decrementRemainingSeats(@Param("flightId") Long flightId,
                                @Param("count") int count);

    int incrementRemainingSeats(@Param("flightId") Long flightId,
                                @Param("count") int count);

    void setRemainingSeats(@Param("flightId") Long flightId,
                           @Param("count") int count);

    int updateSeatStatusToSold(@Param("orderId") Long orderId);

    int releaseSeatsByOrderId(@Param("orderId") Long orderId);

    int countSeatsByFlightId(@Param("flightId") Long flightId);

    List<FlightVO> searchAllFlights(@Param("offset") int offset, @Param("size") int size);

    long countAllFlights();

    FlightVO findFlightByIdAnyStatus(@Param("id") Long id);

    boolean existsAirlineById(@Param("id") Long id);

    boolean existsAirportById(@Param("id") Long id);

    int countAvailableSeatsByFlightAndCabin(@Param("flightId") Long flightId,
                                            @Param("cabinClass") String cabinClass);

    List<FlightSeat> findAvailableSeatsByFlightAndCabin(@Param("flightId") Long flightId,
                                                         @Param("cabinClass") String cabinClass,
                                                         @Param("limit") int limit);

    int releaseSoldSeatsByOrderId(@Param("orderId") Long orderId);

    int lockAvailableSeatsForWaitlist(@Param("seatIds") List<Long> seatIds,
                                      @Param("orderId") Long orderId);

    java.math.BigDecimal findMinPriceByFlightAndCabin(@Param("flightId") Long flightId,
                                                       @Param("cabinClass") String cabinClass);

    List<String> findCabinClassesByOrderId(@Param("orderId") Long orderId);

    Long findAirlineIdByCodeOrName(@Param("code") String code, @Param("name") String name);

    int releaseSoldSeatsBySeatIds(@Param("seatIds") List<Long> seatIds);

    int sellAvailableSeatsByIds(@Param("seatIds") List<Long> seatIds,
                                @Param("orderId") Long orderId);

    List<Flight> findSameRouteFlights(@Param("departureAirportId") Long departureAirportId,
                                      @Param("arrivalAirportId") Long arrivalAirportId,
                                      @Param("excludeFlightId") Long excludeFlightId,
                                      @Param("passengerCount") int passengerCount);
}
