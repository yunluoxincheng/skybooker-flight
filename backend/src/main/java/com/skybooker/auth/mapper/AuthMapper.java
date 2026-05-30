package com.skybooker.auth.mapper;

import com.skybooker.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthMapper {

    User findByEmail(@Param("email") String email);

    User findById(@Param("id") Long id);

    void insertUser(User user);

    void updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    void insertVerificationCodeLog(@Param("target") String target,
                                   @Param("targetType") String targetType,
                                   @Param("scene") String scene,
                                   @Param("sendStatus") String sendStatus,
                                   @Param("ipAddress") String ipAddress);

    List<User> findUsersByRole(@Param("role") String role, @Param("offset") int offset, @Param("size") int size);

    long countUsersByRole(@Param("role") String role);

    void updateUserStatus(@Param("id") Long id, @Param("status") String status);
}
