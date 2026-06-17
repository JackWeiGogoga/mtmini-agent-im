package com.gogoga.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.exception.AuthenticationException;
import com.gogoga.backend.exception.NoPermissionException;
import com.gogoga.backend.exception.OptimisticLockException;
import com.gogoga.backend.vo.ResultVO;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * @author JackWei
 * @since 2026-01-21
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionResolver {

    /**
     * 处理乐观锁冲突异常
     *
     * @param e       异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(OptimisticLockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResultVO<Void> handleOptimisticLockException(OptimisticLockException e, HttpServletRequest request) {
        log.warn("乐观锁冲突异常: {}, URL: {}", e.getMessage(), request.getRequestURI());
        return ResultVO.conflict(e.getMessage());
    }

    /**
     * 处理业务异常
     *
     * @param e       异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResultVO<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {}, URL: {}", e.getMessage(), request.getRequestURI());
        return ResultVO.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理认证业务异常
     *
     * @param e       异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResultVO<Void> handleAuthenticationException(AuthenticationException e, HttpServletRequest request) {
        log.warn("认证异常: {}, code={}, URL: {}", e.getMessage(), e.getErrorCode(), request.getRequestURI());
        return ResultVO.error(resolveAuthenticationCode(e), e.getMessage());
    }

    /**
     * 处理无权限异常
     *
     * @param e       异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(NoPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResultVO<Void> handleNoPermissionException(NoPermissionException e, HttpServletRequest request) {
        log.warn("无权限访问: {}, URL: {}, 权限: {}", e.getMessage(), request.getRequestURI(), e.getPermission());
        return ResultVO.forbidden(e.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid）
     *
     * @param e       异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultVO<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}, URL: {}", message, request.getRequestURI());
        return ResultVO.badRequest(message);
    }

    /**
     * 处理绑定异常
     *
     * @param e       异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultVO<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}, URL: {}", message, request.getRequestURI());
        return ResultVO.badRequest(message);
    }

    /**
     * 处理非法参数异常
     *
     * @param e       异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultVO<Void> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("非法参数: {}, URL: {}", e.getMessage(), request.getRequestURI());
        return ResultVO.badRequest(e.getMessage());
    }

    /**
     * 处理其他未知异常
     *
     * @param e       异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResultVO<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {}, URL: {}", e.getMessage(), request.getRequestURI(), e);
        return ResultVO.error("系统错误，请稍后再试");
    }

    private int resolveAuthenticationCode(AuthenticationException e) {
        String errorCode = e.getErrorCode();
        if (AuthenticationException.SMS_SEND_TOO_FREQUENT.equals(errorCode)
                || AuthenticationException.SMS_SEND_LIMIT_EXCEEDED.equals(errorCode)
                || AuthenticationException.SMS_IP_RATE_LIMIT_EXCEEDED.equals(errorCode)
                || AuthenticationException.SMS_LOGIN_RETRY_LIMIT_EXCEEDED.equals(errorCode)
                || AuthenticationException.INVALID_PHONE.equals(errorCode)
                || AuthenticationException.INVALID_SMS_CODE.equals(errorCode)
                || AuthenticationException.SMS_CODE_EXPIRED.equals(errorCode)
                || AuthenticationException.PASSWORD_TOO_WEAK.equals(errorCode)
                || AuthenticationException.EMAIL_ALREADY_EXISTS.equals(errorCode)
                || AuthenticationException.PHONE_ALREADY_EXISTS.equals(errorCode)) {
            return 400;
        }

        if (AuthenticationException.USER_DISABLED.equals(errorCode)) {
            return 403;
        }

        return 401;
    }
}
