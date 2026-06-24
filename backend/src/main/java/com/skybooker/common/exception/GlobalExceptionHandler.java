package com.skybooker.common.exception;

import com.skybooker.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.debug("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(resolveHttpStatus(e))
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), message);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleRequestParameterException(Exception e) {
        return ApiResponse.error(ErrorCode.VALIDATION_ERROR.getCode(), ErrorCode.VALIDATION_ERROR.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException e) {
        return ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
        return ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleBadCredentialsException(BadCredentialsException e) {
        return ApiResponse.error(ErrorCode.INVALID_CREDENTIALS.getCode(), ErrorCode.INVALID_CREDENTIALS.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return ApiResponse.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }

    private HttpStatus resolveHttpStatus(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode == null) {
            return HttpStatus.OK;
        }

        return switch (errorCode) {
            case INVALID_CREDENTIALS, UNAUTHORIZED, TOKEN_INVALID, TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, ACCOUNT_DISABLED, ACCOUNT_TYPE_MISMATCH, ADMIN_PROFILE_DISABLED -> HttpStatus.FORBIDDEN;
            case VALIDATION_ERROR, VERIFICATION_CODE_INVALID, VERIFICATION_CODE_SEND_TOO_FREQUENT,
                 VERIFICATION_CODE_DAILY_LIMIT, EMAIL_ALREADY_REGISTERED, PASSWORD_MISMATCH,
                 IP_CODE_LIMIT_EXCEEDED, VERIFICATION_CODE_MAX_ATTEMPTS, SCENE_NOT_SUPPORTED,
                 VERIFICATION_EMAIL_SEND_FAILED -> HttpStatus.BAD_REQUEST;
            case LOGIN_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FLIGHT_NOT_SELLABLE, SEAT_NOT_AVAILABLE, SEAT_LOCK_FAILED, ORDER_STATE_INVALID,
                 ORDER_EXPIRED, SEAT_ALREADY_EXISTS, PASSENGER_HAS_ORDERS, DUPLICATE_PASSENGER,
                 DUPLICATE_SEAT_IN_ORDER, DUPLICATE_PASSENGER_IN_ORDER, FLIGHT_HAS_INVENTORY,
                 ADMIN_ACCOUNT_PROTECTED, REFUND_WINDOW_CLOSED, CHANGE_WINDOW_CLOSED, WAITLIST_NOT_FOUND,
                 WAITLIST_STATE_INVALID, WAITLIST_NOT_NEEDED, DUPLICATE_WAITLIST_PASSENGER
                 -> HttpStatus.BAD_REQUEST;
            case SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
