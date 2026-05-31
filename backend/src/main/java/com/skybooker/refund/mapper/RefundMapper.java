package com.skybooker.refund.mapper;

import com.skybooker.refund.entity.RefundRecord;
import com.skybooker.refund.vo.RefundVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundMapper {

    void insertRefundRecord(RefundRecord record);

    RefundVO findByOrderId(@Param("orderId") Long orderId);
}
