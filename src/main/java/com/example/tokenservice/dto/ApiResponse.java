package com.example.tokenservice.dto;

import com.example.tokenservice.exception.ErrorCode;

/**
 * 统一API响应封装类
 * 用于标准化所有API的响应格式
 * @param <T> 响应数据类型
 */
public class ApiResponse<T> {

    private boolean success;
    private int code;
    private String message;
    private T data;

    public ApiResponse() {
        this.code = ErrorCode.SUCCESS.getCode();
        this.success = true;
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setCode(ErrorCode.SUCCESS.getCode());
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setCode(ErrorCode.SUCCESS.getCode());
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setCode(ErrorCode.SYSTEM_ERROR.getCode());
        response.setMessage(message);
        return response;
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return response;
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setCode(errorCode.getCode());
        response.setMessage(message != null ? message : errorCode.getMessage());
        return response;
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setCode(errorCode.getCode());
        response.setMessage(message != null ? message : errorCode.getMessage());
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> tooManyRequests(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setCode(ErrorCode.TOO_MANY_REQUESTS.getCode());
        response.setMessage(message != null ? message : ErrorCode.TOO_MANY_REQUESTS.getMessage());
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
