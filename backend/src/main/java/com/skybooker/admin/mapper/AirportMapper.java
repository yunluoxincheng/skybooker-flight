package com.skybooker.admin.mapper;

import com.skybooker.admin.dto.AdminKeywordStatusQueryDTO;
import com.skybooker.admin.entity.Airport;
import com.skybooker.admin.vo.AirportVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AirportMapper {

    List<AirportVO> search(@Param("query") AdminKeywordStatusQueryDTO query,
                           @Param("offset") int offset,
                           @Param("size") int size);

    long count(@Param("query") AdminKeywordStatusQueryDTO query);

    AirportVO findById(@Param("id") Long id);

    /**
     * 仅用于新增时校验 code 唯一。编辑不允许改 code，故无需排除自身。
     */
    boolean existsByCode(@Param("code") String code);

    void insert(Airport airport);

    /**
     * 仅更新 name / city / province，code 与 status 不在此变更。
     */
    int update(Airport airport);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 物理删除机场。仅在 Service 层确认无关联航班后调用，
     * 否则会被 flight.departure_airport_id / arrival_airport_id 的 FK RESTRICT 兜底拒绝。
     */
    int deleteById(@Param("id") Long id);
}
