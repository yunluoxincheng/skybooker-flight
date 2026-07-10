package com.skybooker.admin.support;

import com.skybooker.admin.dto.AdminFlightQueryDTO;
import com.skybooker.admin.dto.AdminKeywordStatusQueryDTO;
import com.skybooker.admin.dto.AdminOrderQueryDTO;
import com.skybooker.admin.dto.AdminUserQueryDTO;
import com.skybooker.admin.dto.PageQueryDTO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;

public final class AdminListQuerySupport {

    public static final int MAX_PAGE_SIZE = 100;

    private AdminListQuerySupport() {
    }

    public static void normalize(AdminFlightQueryDTO query) {
        validatePage(query);
        query.setKeyword(trimToNull(query.getKeyword()));
        query.setFlightNo(trimToNull(query.getFlightNo()));
        query.setDepartureCity(trimToNull(query.getDepartureCity()));
        query.setArrivalCity(trimToNull(query.getArrivalCity()));
        query.setStatus(trimToNull(query.getStatus()));
        query.setPublishStatus(trimToNull(query.getPublishStatus()));
        query.setDepartureDateStart(trimToNull(query.getDepartureDateStart()));
        query.setDepartureDateEnd(trimToNull(query.getDepartureDateEnd()));
    }

    public static void normalize(AdminKeywordStatusQueryDTO query) {
        validatePage(query);
        query.setKeyword(trimToNull(query.getKeyword()));
        query.setStatus(trimToNull(query.getStatus()));
    }

    public static void normalize(AdminOrderQueryDTO query) {
        validatePage(query);
        query.setStatus(trimToNull(query.getStatus()));
        query.setOrderNo(trimToNull(query.getOrderNo()));
        query.setUserKeyword(trimToNull(query.getUserKeyword()));
        query.setFlightNo(trimToNull(query.getFlightNo()));
        query.setFlightKeyword(trimToNull(query.getFlightKeyword()));
        query.setDepartureDateStart(trimToNull(query.getDepartureDateStart()));
        query.setDepartureDateEnd(trimToNull(query.getDepartureDateEnd()));
    }

    public static void normalize(AdminUserQueryDTO query) {
        validatePage(query);
        query.setKeyword(trimToNull(query.getKeyword()));
        query.setEmail(trimToNull(query.getEmail()));
        query.setNickname(trimToNull(query.getNickname()));
        query.setStatus(trimToNull(query.getStatus()));
    }

    public static void validatePage(PageQueryDTO query) {
        if (query.getPage() < 1 || query.getSize() < 1 || query.getSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    public static int offset(PageQueryDTO query) {
        validatePage(query);
        long offset = ((long) query.getPage() - 1) * query.getSize();
        if (offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return (int) offset;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
