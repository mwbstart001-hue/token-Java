package com.example.tokenservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void handleTokenExpiredException_ShouldReturnCorrectResponse() throws Exception {
        mockMvc.perform(get("/test/exception/token-expired"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.TOKEN_EXPIRED.getCode())))
                .andExpect(jsonPath("$.message", is("测试：Token已过期")));
    }

    @Test
    void handleTokenInvalidException_ShouldReturnCorrectResponse() throws Exception {
        mockMvc.perform(get("/test/exception/token-invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.TOKEN_INVALID.getCode())))
                .andExpect(jsonPath("$.message", is("Token无效")));
    }

    @Test
    void handleTokenInvalidException_WithSignatureError_ShouldReturnCorrectErrorCode() throws Exception {
        mockMvc.perform(get("/test/exception/token-invalid-signature"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.TOKEN_SIGNATURE_INVALID.getCode())))
                .andExpect(jsonPath("$.message", is("Token签名无效")));
    }

    @Test
    void handleTokenInvalidException_WithFormatError_ShouldReturnCorrectErrorCode() throws Exception {
        mockMvc.perform(get("/test/exception/token-invalid-format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.TOKEN_MALFORMED.getCode())))
                .andExpect(jsonPath("$.message", is("Token格式错误")));
    }

    @Test
    void handleTokenNotFoundException_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/test/exception/token-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.TOKEN_NOT_FOUND.getCode())))
                .andExpect(jsonPath("$.message", is("测试：Token不存在")));
    }

    @Test
    void handleBusinessException_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/test/exception/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.SYSTEM_ERROR.getCode())))
                .andExpect(jsonPath("$.message", is("测试：业务异常")));
    }

    @Test
    void handleIllegalArgumentException_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/test/exception/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.PARAM_VALIDATION_FAILED.getCode())))
                .andExpect(jsonPath("$.message", is("请求参数无效")));
    }

    @Test
    void handleNullPointerException_ShouldReturnInternalServerError() throws Exception {
        mockMvc.perform(get("/test/exception/null-pointer"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.SYSTEM_ERROR.getCode())))
                .andExpect(jsonPath("$.message", is("服务器内部错误，请稍后重试")));
    }

    @Test
    void allErrorResponses_ShouldHaveCorrectStructure() throws Exception {
        mockMvc.perform(get("/test/exception/token-expired"))
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void errorResponse_ContentType_ShouldBeJson() throws Exception {
        mockMvc.perform(get("/test/exception/token-invalid"))
                .andExpect(content().contentType("application/json"));
    }
}
