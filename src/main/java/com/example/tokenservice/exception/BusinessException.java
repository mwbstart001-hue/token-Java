package com.example.tokenservice.exception;

/**
 * 业务异常基类
 * 所有业务相关的异常都应该继承此类
 * 包含错误码和详细消息
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detailMessage;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detailMessage = errorCode.getMessage();
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage != null ? detailMessage : errorCode.getMessage());
        this.errorCode = errorCode;
        this.detailMessage = detailMessage != null ? detailMessage : errorCode.getMessage();
    }

    public BusinessException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(detailMessage != null ? detailMessage : errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detailMessage = detailMessage != null ? detailMessage : errorCode.getMessage();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetailMessage() {
        return detailMessage;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
