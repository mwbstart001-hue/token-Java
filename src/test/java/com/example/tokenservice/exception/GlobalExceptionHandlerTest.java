package com.example.tokenservice.exception;

import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.dto.TokenGenerateRequest;
import com.example.tokenservice.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenProperties tokenProperties;

    @BeforeEach
    void setUp() {
        TokenProperties.ApiKey apiKeyConfig = new TokenProperties.ApiKey();
        apiKeyConfig.setEnabled(false);
        tokenProperties.setApiKey(apiKeyConfig);
    }

    @Test
    void handleValidationExceptions_WhenMissingRequiredFields_ShouldReturnError() throws Exception {
        TokenGenerateRequest request = new TokenGenerateRequest();

        mockMvc.perform(post("/api/token/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.PARAM_VALIDATION_FAILED.getCode())))
                .andExpect(jsonPath("$.message", is("参数校验失败")));
    }

    @Test
    void handleIllegalArgumentException_WhenInvalidParameter_ShouldReturnError() throws Exception {
        String invalidToken = "invalid-token-with-special-characters-!@#$%";

        mockMvc.perform(get("/api/token/validate")
                .param("token", invalidToken))
                .andExpect(status().isOk());
    }

    @Test
    void getTokenInfo_WhenTokenNotExists_ShouldReturnNotFoundError() throws Exception {
        String nonExistentToken = "non-existent-token-12345";
        when(tokenService.getTokenInfo(nonExistentToken)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/token/info")
                .param("token", nonExistentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Token 不存在")));
    }

    @Test
    void invalidateToken_WhenTokenNotExists_ShouldReturnError() throws Exception {
        String nonExistentToken = "non-existent-token-12345";
        when(tokenService.invalidateToken(nonExistentToken)).thenReturn(false);

        mockMvc.perform(post("/api/token/invalidate")
                .param("token", nonExistentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Token 不存在或已作废")));
    }
}
