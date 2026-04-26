package com.example.tokenservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomExceptionTest {

    @Test
    void businessException_ShouldHaveCorrectErrorCode() {
        BusinessException ex = new BusinessException(ErrorCode.SYSTEM_ERROR);
        assertEquals(ErrorCode.SYSTEM_ERROR, ex.getErrorCode());
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), ex.getDetailMessage());
        assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void businessException_WithDetailMessage_ShouldOverrideMessage() {
        String detailMessage = "自定义错误信息";
        BusinessException ex = new BusinessException(ErrorCode.SYSTEM_ERROR, detailMessage);
        assertEquals(ErrorCode.SYSTEM_ERROR, ex.getErrorCode());
        assertEquals(detailMessage, ex.getDetailMessage());
        assertEquals(detailMessage, ex.getMessage());
    }

    @Test
    void authenticationException_ShouldHaveCorrectErrorCode() {
        AuthenticationException ex = new AuthenticationException();
        assertEquals(ErrorCode.AUTH_FAILED, ex.getErrorCode());
        assertEquals(ErrorCode.AUTH_FAILED.getCode(), ex.getCode());
    }

    @Test
    void authenticationException_WithSpecificErrorCode_ShouldWork() {
        AuthenticationException ex = new AuthenticationException(ErrorCode.AUTH_MISSING_API_KEY);
        assertEquals(ErrorCode.AUTH_MISSING_API_KEY, ex.getErrorCode());
        assertEquals(ErrorCode.AUTH_MISSING_API_KEY.getCode(), ex.getCode());
    }

    @Test
    void tokenExpiredException_ShouldHaveCorrectErrorCode() {
        TokenExpiredException ex = new TokenExpiredException();
        assertEquals(ErrorCode.TOKEN_EXPIRED, ex.getErrorCode());
        assertEquals(ErrorCode.TOKEN_EXPIRED.getCode(), ex.getCode());
    }

    @Test
    void tokenExpiredException_WithDetailMessage_ShouldWork() {
        String message = "Token已过期，请重新登录";
        TokenExpiredException ex = new TokenExpiredException(message);
        assertEquals(ErrorCode.TOKEN_EXPIRED, ex.getErrorCode());
        assertEquals(message, ex.getDetailMessage());
    }

    @Test
    void tokenInvalidException_ShouldHaveCorrectErrorCode() {
        TokenInvalidException ex = new TokenInvalidException();
        assertEquals(ErrorCode.TOKEN_INVALID, ex.getErrorCode());
    }

    @Test
    void tokenInvalidException_WithSpecificErrorCode_ShouldWork() {
        TokenInvalidException ex = new TokenInvalidException(ErrorCode.TOKEN_SIGNATURE_INVALID);
        assertEquals(ErrorCode.TOKEN_SIGNATURE_INVALID, ex.getErrorCode());
    }

    @Test
    void tokenNotFoundException_ShouldHaveCorrectErrorCode() {
        TokenNotFoundException ex = new TokenNotFoundException();
        assertEquals(ErrorCode.TOKEN_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void tokenNotFoundException_WithDetailMessage_ShouldWork() {
        String message = "请求的Token不存在";
        TokenNotFoundException ex = new TokenNotFoundException(message);
        assertEquals(ErrorCode.TOKEN_NOT_FOUND, ex.getErrorCode());
        assertEquals(message, ex.getDetailMessage());
    }

    @Test
    void exceptionHierarchy_ShouldBeCorrect() {
        assertTrue(new BusinessException(ErrorCode.SYSTEM_ERROR) instanceof RuntimeException);
        assertTrue(new AuthenticationException() instanceof BusinessException);
        assertTrue(new TokenExpiredException() instanceof BusinessException);
        assertTrue(new TokenInvalidException() instanceof BusinessException);
        assertTrue(new TokenNotFoundException() instanceof BusinessException);
    }
}
