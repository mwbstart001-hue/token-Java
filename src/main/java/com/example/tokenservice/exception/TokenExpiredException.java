package com.example.tokenservice.exception;

/**
 * Token过期异常
 * 当Token已过期但签名有效时抛出
 */
public class TokenExpiredException extends BusinessException {

    public TokenExpiredException() {
        super(ErrorCode.TOKEN_EXPIRED);
    }

    public TokenExpiredException(String detailMessage) {
        super(ErrorCode.TOKEN_EXPIRED, detailMessage);
    }

    public TokenExpiredException(String detailMessage, Throwable cause) {
        super(ErrorCode.TOKEN_EXPIRED, detailMessage, cause);
    }
}
