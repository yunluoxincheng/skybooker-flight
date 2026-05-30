package com.skybooker.admin.mapper;

import com.skybooker.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMapper {

    AdminUser findByUsername(@Param("username") String username);

    AdminUser findByUserId(@Param("userId") Long userId);
}
