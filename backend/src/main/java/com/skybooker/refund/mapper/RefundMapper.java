package com.skybooker.refund.mapper;

import com.skybooker.refund.entity.RefundRecord;
import com.skybooker.refund.vo.RefundVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RefundMapper {

    void insertRefundRecord(RefundRecord record);

    RefundVO findByOrderId(@Param("orderId") Long orderId);

    List<RefundRecord> findRecordsByOrderId(@Param("orderId") Long orderId);
}
