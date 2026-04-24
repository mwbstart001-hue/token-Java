package com.example.tokenservice.exception;

/**
 * 认证失败异常
 * 当API密钥认证失败时抛出
 */
public class AuthenticationException extends BusinessException {

    public AuthenticationException() {
        super(ErrorCode.AUTH_FAILED);
    }

    public AuthenticationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthenticationException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public AuthenticationException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }
}
