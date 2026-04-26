package com.example.tokenservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void successCode_ShouldBeZero() {
        assertEquals(0, ErrorCode.SUCCESS.getCode());
        assertEquals("操作成功", ErrorCode.SUCCESS.getMessage());
    }

    @Test
    void authErrorCodes_ShouldHaveCorrectValues() {
        assertEquals(1001, ErrorCode.AUTH_FAILED.getCode());
        assertEquals("认证失败", ErrorCode.AUTH_FAILED.getMessage());

        assertEquals(1002, ErrorCode.AUTH_MISSING_API_KEY.getCode());
        assertEquals("缺少API密钥", ErrorCode.AUTH_MISSING_API_KEY.getMessage());

        assertEquals(1003, ErrorCode.AUTH_INVALID_API_KEY.getCode());
        assertEquals("无效的API密钥", ErrorCode.AUTH_INVALID_API_KEY.getMessage());
    }

    @Test
    void tokenErrorCodes_ShouldHaveCorrectValues() {
        assertEquals(2001, ErrorCode.TOKEN_EXPIRED.getCode());
        assertEquals("Token已过期", ErrorCode.TOKEN_EXPIRED.getMessage());

        assertEquals(2002, ErrorCode.TOKEN_INVALID.getCode());
        assertEquals("Token无效", ErrorCode.TOKEN_INVALID.getMessage());

        assertEquals(2003, ErrorCode.TOKEN_NOT_FOUND.getCode());
        assertEquals("Token不存在", ErrorCode.TOKEN_NOT_FOUND.getMessage());

        assertEquals(2004, ErrorCode.TOKEN_ALREADY_INVALIDATED.getCode());
        assertEquals("Token已作废", ErrorCode.TOKEN_ALREADY_INVALIDATED.getMessage());

        assertEquals(2005, ErrorCode.TOKEN_SIGNATURE_INVALID.getCode());
        assertEquals("Token签名无效", ErrorCode.TOKEN_SIGNATURE_INVALID.getMessage());

        assertEquals(2006, ErrorCode.TOKEN_MALFORMED.getCode());
        assertEquals("Token格式错误", ErrorCode.TOKEN_MALFORMED.getMessage());

        assertEquals(2007, ErrorCode.TOKEN_UNSUPPORTED.getCode());
        assertEquals("不支持的Token类型", ErrorCode.TOKEN_UNSUPPORTED.getMessage());
    }

    @Test
    void paramErrorCode_ShouldHaveCorrectValues() {
        assertEquals(3001, ErrorCode.PARAM_VALIDATION_FAILED.getCode());
        assertEquals("参数校验失败", ErrorCode.PARAM_VALIDATION_FAILED.getMessage());
    }

    @Test
    void systemErrorCode_ShouldHaveCorrectValues() {
        assertEquals(9001, ErrorCode.SYSTEM_ERROR.getCode());
        assertEquals("系统内部错误", ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
