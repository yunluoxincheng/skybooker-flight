package com.skybooker.flight.mapper;

import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.entity.FlightSeat;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.flight.vo.FlightSeatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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

    void releaseSeatsByOrderId(@Param("orderId") Long orderId);

    int countSeatsByFlightId(@Param("flightId") Long flightId);

    List<FlightVO> searchAllFlights(@Param("offset") int offset, @Param("size") int size);

    long countAllFlights();

    FlightVO findFlightByIdAnyStatus(@Param("id") Long id);
}
