package com.skybooker.passenger.mapper;

import com.skybooker.passenger.entity.Passenger;
import com.skybooker.passenger.vo.PassengerVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PassengerMapper {
    List<PassengerVO> findByUserId(@Param("userId") Long userId);

    Passenger findById(@Param("id") Long id);

    void insert(Passenger passenger);

    void update(Passenger passenger);

    void deleteById(@Param("id") Long id);

    boolean existsByIdCardNo(@Param("userId") Long userId, @Param("idCardNo") String idCardNo, @Param("excludeId") Long excludeId);

    boolean hasOrderHistory(@Param("id") Long id);
}
