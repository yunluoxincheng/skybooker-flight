package com.skybooker.admin.mapper;

import com.skybooker.admin.entity.Airline;
import com.skybooker.admin.vo.AirlineVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AirlineMapper {

    List<AirlineVO> search(@Param("keyword") String keyword,
                           @Param("status") String status,
                           @Param("offset") int offset,
                           @Param("size") int size);

    long count(@Param("keyword") String keyword, @Param("status") String status);

    AirlineVO findById(@Param("id") Long id);

    /**
     * 仅用于新增时校验 code 唯一。编辑不允许改 code，故无需排除自身。
     */
    boolean existsByCode(@Param("code") String code);

    void insert(Airline airline);

    /**
     * 仅更新 name / logoUrl，code 与 status 不在此变更（status 由 {@link #updateStatus} 控制）。
     */
    int update(Airline airline);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
