package com.skybooker.auth.mapper;

import com.skybooker.admin.vo.UserAdminVO;
import com.skybooker.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthMapper {

    User findByEmail(@Param("email") String email);

    User findById(@Param("id") Long id);

    void insertUser(User user);

    void insertByAdmin(User user);

    void updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    void insertVerificationCodeLog(@Param("target") String target,
                                   @Param("targetType") String targetType,
                                   @Param("scene") String scene,
                                   @Param("sendStatus") String sendStatus,
                                   @Param("ipAddress") String ipAddress);

    List<UserAdminVO> findUsersByRole(@Param("role") String role, @Param("offset") int offset, @Param("size") int size);

    long countUsersByRole(@Param("role") String role);

    void updateUserStatus(@Param("id") Long id, @Param("status") String status);

    int updateStatusCAS(@Param("id") Long id,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus);

    int countActiveOrdersByUserId(@Param("userId") Long userId);

    boolean existsPendingWaitlistByUserId(@Param("userId") Long userId);

    boolean existsProcessingRefundOrChangeByUserId(@Param("userId") Long userId);

    // ---- 硬删除预检查:统计所有业务引用(FK RESTRICT 不分状态) ----

    int countAllOrdersByUserId(@Param("userId") Long userId);

    int countAllPassengersByUserId(@Param("userId") Long userId);

    int countAllWaitlistByUserId(@Param("userId") Long userId);

    int countAllRefundOrChangeByUserId(@Param("userId") Long userId);

    boolean existsOauthBindingByUserId(@Param("userId") Long userId);

    /**
     * 物理删除用户。仅在 Service 层确认零业务数据后调用,
     * 否则会被 ticket_order / passenger 等表的 FK RESTRICT 兜底拒绝。
     */
    int deleteUserById(@Param("id") Long id);
}
