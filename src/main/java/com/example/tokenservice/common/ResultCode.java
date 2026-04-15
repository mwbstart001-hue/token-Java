package com.example.tokenservice.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    
    SUCCESS(200, "操作成功"),
    
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    
    TOKEN_INVALID(1001, "Token 无效"),
    TOKEN_EXPIRED(1002, "Token 已过期"),
    TOKEN_REVOKED(1003, "Token 已作废"),
    TOKEN_SIGNATURE_ERROR(1004, "Token 签名验证失败"),
    
    INTERNAL_ERROR(500, "系统内部错误");
    
    private final int code;
    private final String message;
}
