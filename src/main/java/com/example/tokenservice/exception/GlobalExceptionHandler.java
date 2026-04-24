package com.example.tokenservice.exception;

import com.example.tokenservice.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理所有未捕获的异常
 * 记录详细日志，返回标准化的错误响应
 * 不暴露内部实现细节给客户端
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理认证异常
     * 当API密钥认证失败时抛出
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException ex) {
        log.warn("认证失败 - 错误码: {}, 消息: {}", ex.getErrorCode().getCode(), ex.getMessage());
        return ApiResponse.error(ex.getErrorCode(), ex.getDetailMessage());
    }

    /**
     * 处理业务异常基类
     * 所有自定义业务异常的统一处理
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        log.warn("业务异常 - 错误码: {}, 消息: {}", ex.getErrorCode().getCode(), ex.getMessage());
        return ApiResponse.error(ex.getErrorCode(), ex.getDetailMessage());
    }

    /**
     * 处理Token过期异常
     */
    @ExceptionHandler(TokenExpiredException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTokenExpiredException(TokenExpiredException ex) {
        log.debug("Token已过期 - 消息: {}", ex.getMessage());
        return ApiResponse.error(ex.getErrorCode(), ex.getDetailMessage());
    }

    /**
     * 处理Token无效异常
     */
    @ExceptionHandler(TokenInvalidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTokenInvalidException(TokenInvalidException ex) {
        log.warn("Token无效 - 错误码: {}, 消息: {}", ex.getErrorCode().getCode(), ex.getMessage());
        return ApiResponse.error(ex.getErrorCode(), ex.getDetailMessage());
    }

    /**
     * 处理Token不存在异常
     */
    @ExceptionHandler(TokenNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleTokenNotFoundException(TokenNotFoundException ex) {
        log.debug("Token不存在 - 消息: {}", ex.getMessage());
        return ApiResponse.error(ex.getErrorCode(), ex.getDetailMessage());
    }

    /**
     * 处理参数校验异常
     * 当@Valid校验失败时触发
     * 返回详细的字段错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
            log.debug("参数校验失败 - 字段: {}, 错误: {}", fieldName, errorMessage);
        });

        log.warn("参数校验失败 - 错误字段数: {}", errors.size());
        return ApiResponse.error(ErrorCode.PARAM_VALIDATION_FAILED, "参数校验失败", errors);
    }

    /**
     * 处理非法参数异常
     * 当方法接收到非法参数时触发
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("非法参数异常: {}", ex.getMessage());
        return ApiResponse.error(ErrorCode.PARAM_VALIDATION_FAILED, "请求参数无效");
    }

    /**
     * 处理空指针异常
     * 记录详细日志，返回通用错误信息
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleNullPointerException(NullPointerException ex) {
        log.error("空指针异常", ex);
        return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "服务器内部错误，请稍后重试");
    }

    /**
     * 处理所有未捕获的异常
     * 作为最后的防线，记录详细日志，返回通用错误信息
     * 不暴露异常的具体信息给客户端（安全考虑）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGlobalException(Exception ex) {
        log.error("未捕获的系统异常", ex);
        return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "服务器内部错误，请稍后重试");
    }
}
