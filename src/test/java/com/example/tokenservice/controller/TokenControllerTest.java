package com.example.tokenservice.controller;

import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.dto.TokenGenerateRequest;
import com.example.tokenservice.dto.TokenInfo;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TokenController.class)
class TokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private TokenProperties tokenProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_TOKEN = "eyJhbGciOiJIUzI1NiJ9.test.token";

    @BeforeEach
    void setUp() {
        when(tokenProperties.getSecret()).thenReturn("test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm");
        when(tokenProperties.getDefaultExpireSeconds()).thenReturn(3600L);
    }

    @Test
    void generateToken_WithValidRequest_ShouldReturnToken() throws Exception {
        TokenGenerateRequest request = new TokenGenerateRequest();
        request.setUserId("user123");
        request.setSubject("test-subject");

        when(tokenService.generateToken(eq("user123"), eq("test-subject"), isNull())).thenReturn(TEST_TOKEN);

        mockMvc.perform(post("/api/token/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token", is(TEST_TOKEN)));
    }

    @Test
    void generateToken_WithMissingUserId_ShouldReturnBadRequest() throws Exception {
        TokenGenerateRequest request = new TokenGenerateRequest();

        mockMvc.perform(post("/api/token/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateToken_WithValidToken_ShouldReturnTrue() throws Exception {
        when(tokenService.validateToken(TEST_TOKEN)).thenReturn(true);

        mockMvc.perform(get("/api/token/validate")
                .param("token", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.valid", is(true)));
    }

    @Test
    void validateToken_WithInvalidToken_ShouldReturnFalse() throws Exception {
        when(tokenService.validateToken("invalid-token")).thenReturn(false);

        mockMvc.perform(get("/api/token/validate")
                .param("token", "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.valid", is(false)));
    }

    @Test
    void getTokenInfo_WithValidToken_ShouldReturnInfo() throws Exception {
        TokenInfo tokenInfo = createTokenInfo(TEST_TOKEN, "user123", TokenStatus.ACTIVE, true);
        when(tokenService.getTokenInfo(TEST_TOKEN)).thenReturn(Optional.of(tokenInfo));

        mockMvc.perform(get("/api/token/info")
                .param("token", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is("user123")))
                .andExpect(jsonPath("$.data.valid", is(true)));
    }

    @Test
    void getTokenInfo_WithNonExistentToken_ShouldReturnError() throws Exception {
        when(tokenService.getTokenInfo("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/token/info")
                .param("token", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void invalidateToken_WithActiveToken_ShouldReturnSuccess() throws Exception {
        when(tokenService.invalidateToken(TEST_TOKEN)).thenReturn(true);

        mockMvc.perform(post("/api/token/invalidate")
                .param("token", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.success", is(true)));
    }

    @Test
    void invalidateToken_WithInvalidToken_ShouldReturnError() throws Exception {
        when(tokenService.invalidateToken("invalid")).thenReturn(false);

        mockMvc.perform(post("/api/token/invalidate")
                .param("token", "invalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)));
    }

    private TokenInfo createTokenInfo(String token, String userId, TokenStatus status, boolean valid) {
        TokenInfo info = new TokenInfo();
        info.setTokenValue(token);
        info.setUserId(userId);
        info.setSubject("test");
        info.setIssuedAt(LocalDateTime.now());
        info.setExpiresAt(LocalDateTime.now().plusHours(1));
        info.setStatus(status);
        info.setValid(valid);
        return info;
    }
}
