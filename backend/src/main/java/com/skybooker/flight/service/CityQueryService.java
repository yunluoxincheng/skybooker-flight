package com.skybooker.flight.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CityQueryService {

    private static final int MAX_CITY_LENGTH = 50;
    private static final String CITY_SUFFIX = "市";

    public String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    public String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_CITY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (trimmed.endsWith(CITY_SUFFIX) && trimmed.length() > CITY_SUFFIX.length()) {
            return trimmed.substring(0, trimmed.length() - CITY_SUFFIX.length());
        }
        return trimmed;
    }

    public List<String> candidates(String normalizedCity) {
        if (normalizedCity == null) {
            return null;
        }
        return List.of(normalizedCity, normalizedCity + CITY_SUFFIX);
    }
}
