package com.example.tokenservice.exception;

/**
 * 错误码枚举类
 * 定义系统中所有的错误码和对应的默认消息
 * 错误码规则：
 * - 1xxx: 认证相关错误
 * - 2xxx: Token相关错误
 * - 3xxx: 参数校验错误
 * - 9xxx: 系统错误
 */
public enum ErrorCode {

    SUCCESS(0, "操作成功"),

    AUTH_FAILED(1001, "认证失败"),
    AUTH_MISSING_API_KEY(1002, "缺少API密钥"),
    AUTH_INVALID_API_KEY(1003, "无效的API密钥"),

    TOKEN_EXPIRED(2001, "Token已过期"),
    TOKEN_INVALID(2002, "Token无效"),
    TOKEN_NOT_FOUND(2003, "Token不存在"),
    TOKEN_ALREADY_INVALIDATED(2004, "Token已作废"),
    TOKEN_SIGNATURE_INVALID(2005, "Token签名无效"),
    TOKEN_MALFORMED(2006, "Token格式错误"),
    TOKEN_UNSUPPORTED(2007, "不支持的Token类型"),

    PARAM_VALIDATION_FAILED(3001, "参数校验失败"),

    SYSTEM_ERROR(9001, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
