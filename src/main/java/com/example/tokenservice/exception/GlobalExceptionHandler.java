package com.example.tokenservice.exception;

import com.example.tokenservice.common.ResultCode;
import com.example.tokenservice.common.TraceContext;
import com.example.tokenservice.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String traceId = TraceContext.getTraceId();
        
        Map<String, String> errors = new HashMap<>();
        BindingResult bindingResult = ex.getBindingResult();
        
        for (FieldError error : bindingResult.getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        
        log.warn("[{}] Parameter validation failed: {}", traceId, errors);
        
        return ApiResponse.<Map<String, String>>builder()
                .code(ResultCode.BAD_REQUEST.getCode())
                .message("参数验证失败")
                .data(errors)
                .build();
    }
    
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingHeaderException(MissingRequestHeaderException ex) {
        String traceId = TraceContext.getTraceId();
        
        log.warn("[{}] Missing request header: {}", traceId, ex.getHeaderName());
        
        return ApiResponse.error(
                ResultCode.BAD_REQUEST.getCode(),
                "缺少必要的请求头: " + ex.getHeaderName()
        );
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        String traceId = TraceContext.getTraceId();
        
        log.warn("[{}] Illegal argument: {}", traceId, ex.getMessage(), ex);
        
        return ApiResponse.error(
                ResultCode.BAD_REQUEST.getCode(),
                ex.getMessage()
        );
    }
    
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleNullPointerException(NullPointerException ex) {
        String traceId = TraceContext.getTraceId();
        
        log.error("[{}] NullPointerException occurred", traceId, ex);
        
        return ApiResponse.error(
                ResultCode.INTERNAL_ERROR.getCode(),
                "系统内部错误"
        );
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGenericException(Exception ex) {
        String traceId = TraceContext.getTraceId();
        
        log.error("[{}] Unexpected exception occurred", traceId, ex);
        
        return ApiResponse.error(
                ResultCode.INTERNAL_ERROR.getCode(),
                "系统内部错误"
        );
    }
}
