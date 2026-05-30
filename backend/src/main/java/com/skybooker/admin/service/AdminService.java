package com.skybooker.admin.service;

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

    public PageResponse<User> listUsers(int page, int size) {
        int offset = (page - 1) * size;
        List<User> users = authMapper.findUsersByRole("USER", offset, size);
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
        List<OrderVO> orders = orderMapper.findByUserId(null, offset, size);
        return new PageResponse<>(orders, orders.size(), page, size);
    }

    public OrderVO getOrderDetail(Long id) {
        return orderMapper.findDetailById(id);
    }
}
