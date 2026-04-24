package com.example.tokenservice.exception;

/**
 * Token无效异常
 * 当Token签名无效、格式错误或不支持时抛出
 */
public class TokenInvalidException extends BusinessException {

    public TokenInvalidException() {
        super(ErrorCode.TOKEN_INVALID);
    }

    public TokenInvalidException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TokenInvalidException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public TokenInvalidException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }
}
