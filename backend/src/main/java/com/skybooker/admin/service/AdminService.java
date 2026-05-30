package com.skybooker.admin.service;

import com.skybooker.admin.vo.UserAdminVO;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AuthMapper authMapper;
    private final OrderMapper orderMapper;

    public PageResponse<UserAdminVO> listUsers(int page, int size) {
        int offset = (page - 1) * size;
        List<UserAdminVO> users = authMapper.findUsersByRole("USER", offset, size);
        long total = authMapper.countUsersByRole("USER");
        return new PageResponse<>(users, total, page, size);
    }

    public void disableUser(Long userId) {
        User user = authMapper.findById(userId);
        if (user == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if ("ADMIN".equals(user.getRole())) throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        authMapper.updateUserStatus(userId, "DISABLED");
    }

    public void enableUser(Long userId) {
        User user = authMapper.findById(userId);
        if (user == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if ("ADMIN".equals(user.getRole())) throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        authMapper.updateUserStatus(userId, "NORMAL");
    }

    public PageResponse<OrderVO> listOrders(int page, int size) {
        int offset = (page - 1) * size;
        List<OrderVO> orders = orderMapper.findAllOrders(offset, size);
        long total = orderMapper.countAllOrders();
        return new PageResponse<>(orders, total, page, size);
    }

    public OrderVO getOrderDetail(Long id) {
        OrderVO order = orderMapper.findDetailById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return order;
    }
}
