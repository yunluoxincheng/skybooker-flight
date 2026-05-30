package com.skybooker.auth.mapper;

import com.skybooker.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

    User findByEmail(@Param("email") String email);

    User findById(@Param("id") Long id);
}
