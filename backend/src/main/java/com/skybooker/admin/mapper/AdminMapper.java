package com.skybooker.admin.mapper;

import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.vo.DashboardSummaryVO;
import com.skybooker.admin.vo.HotRouteVO;
import com.skybooker.admin.vo.OrderStatusDistributionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminMapper {

    AdminUser findByUsername(@Param("username") String username);

    AdminUser findByUserId(@Param("userId") Long userId);

    DashboardSummaryVO selectDashboardSummary();

    List<HotRouteVO> selectHotRoutes(@Param("limit") int limit);

    List<OrderStatusDistributionVO> selectOrderStatusDistribution();
}
