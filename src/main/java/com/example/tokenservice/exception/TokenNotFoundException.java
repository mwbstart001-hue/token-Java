package com.example.tokenservice.exception;

/**
 * Token不存在异常
 * 当请求的Token在存储中不存在时抛出
 */
public class TokenNotFoundException extends BusinessException {

    public TokenNotFoundException() {
        super(ErrorCode.TOKEN_NOT_FOUND);
    }

    public TokenNotFoundException(String detailMessage) {
        super(ErrorCode.TOKEN_NOT_FOUND, detailMessage);
    }
}
