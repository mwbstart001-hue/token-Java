package com.example.tokenservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/exception")
public class ExceptionTestController {

    private static final Logger log = LoggerFactory.getLogger(ExceptionTestController.class);

    @GetMapping("/token-expired")
    public void throwTokenExpiredException() {
        log.debug("测试：抛出 TokenExpiredException");
        throw new TokenExpiredException("测试：Token已过期");
    }

    @GetMapping("/token-invalid")
    public void throwTokenInvalidException() {
        log.debug("测试：抛出 TokenInvalidException");
        throw new TokenInvalidException();
    }

    @GetMapping("/token-invalid-signature")
    public void throwTokenInvalidExceptionWithSignatureError() {
        log.debug("测试：抛出 TokenInvalidException（签名无效）");
        throw new TokenInvalidException(ErrorCode.TOKEN_SIGNATURE_INVALID, "Token签名无效");
    }

    @GetMapping("/token-invalid-format")
    public void throwTokenInvalidExceptionWithFormatError() {
        log.debug("测试：抛出 TokenInvalidException（格式错误）");
        throw new TokenInvalidException(ErrorCode.TOKEN_MALFORMED, "Token格式错误");
    }

    @GetMapping("/token-not-found")
    public void throwTokenNotFoundException() {
        log.debug("测试：抛出 TokenNotFoundException");
        throw new TokenNotFoundException("测试：Token不存在");
    }

    @GetMapping("/business")
    public void throwBusinessException() {
        log.debug("测试：抛出 BusinessException");
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "测试：业务异常");
    }

    @GetMapping("/illegal-argument")
    public void throwIllegalArgumentException() {
        log.debug("测试：抛出 IllegalArgumentException");
        throw new IllegalArgumentException("测试：非法参数");
    }

    @GetMapping("/null-pointer")
    public void throwNullPointerException() {
        log.debug("测试：抛出 NullPointerException");
        throw new NullPointerException("测试：空指针异常");
    }
}
