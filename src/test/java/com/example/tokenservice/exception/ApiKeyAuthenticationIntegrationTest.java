package com.example.tokenservice.exception;

import com.example.tokenservice.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("apikey-test")
class ApiKeyAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @Test
    void whenMissingApiKey_ShouldReturn401WithCorrectErrorCode() throws Exception {
        mockMvc.perform(get("/api/token/validate")
                .param("token", "some-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.AUTH_MISSING_API_KEY.getCode())))
                .andExpect(jsonPath("$.message", is("缺少API密钥")));
    }

    @Test
    void whenInvalidApiKey_ShouldReturn401WithCorrectErrorCode() throws Exception {
        mockMvc.perform(get("/api/token/validate")
                .param("token", "some-token")
                .header("X-API-Key", "invalid-api-key-999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.AUTH_INVALID_API_KEY.getCode())))
                .andExpect(jsonPath("$.message", is("无效的API密钥")));
    }

    @Test
    void whenEmptyApiKey_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/token/validate")
                .param("token", "some-token")
                .header("X-API-Key", ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(ErrorCode.AUTH_MISSING_API_KEY.getCode())));
    }

    @Test
    void whenValidApiKey_ShouldPassAuthentication() throws Exception {
        mockMvc.perform(get("/api/token/validate")
                .param("token", "some-token")
                .header("X-API-Key", "test-valid-api-key-12345"))
                .andExpect(status().isOk());
    }

    @Test
    void responseContentType_ShouldBeJson() throws Exception {
        mockMvc.perform(get("/api/token/validate")
                .param("token", "some-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void errorResponse_ShouldHaveCorrectStructure() throws Exception {
        mockMvc.perform(get("/api/token/validate")
                .param("token", "some-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
