package com.example.tokenservice.strategy;

import com.example.tokenservice.config.JwtKeyManager;
import com.example.tokenservice.config.TokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TokenStrategyTest {

    private TokenProperties tokenProperties;
    private JwtKeyManager jwtKeyManager;

    @BeforeEach
    void setUp() {
        tokenProperties = new TokenProperties();
        tokenProperties.setSecret("test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm");
        tokenProperties.setDefaultExpireSeconds(3600L);
        tokenProperties.setMaxExpireSeconds(86400L * 30);
        tokenProperties.setAlgorithm("RS256");

        jwtKeyManager = new JwtKeyManager(tokenProperties);
        jwtKeyManager.init();
    }

    @Test
    void testRS256TokenGenerator_GenerateToken() {
        RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
        
        TokenGenerationResult result = generator.generate(
                "user-001", 
                "test-subject", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        assertNotNull(result);
        assertNotNull(result.getTokenValue());
        assertFalse(result.getTokenValue().isEmpty());
        assertNotNull(result.getJwtId());
        assertEquals("RS256", generator.getAlgorithm());
    }

    @Test
    void testRS256TokenValidator_ValidateValidToken() {
        RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
        RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
        
        TokenGenerationResult result = generator.generate(
                "user-001", 
                "test-subject", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        TokenValidator.ValidationResult validation = validator.validate(result.getTokenValue());
        
        assertTrue(validation.isValid());
        assertEquals(TokenValidator.ValidationStatus.VALID, validation.getStatus());
        assertNotNull(validation.getClaims());
        assertEquals("user-001", validation.getClaims().get("userId"));
    }

    @Test
    void testRS256TokenValidator_ValidateExpiredToken() {
        RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
        RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
        
        TokenGenerationResult result = generator.generate(
                "user-001", 
                "test-subject", 
                LocalDateTime.now().minusHours(2), 
                LocalDateTime.now().minusHours(1)
        );
        
        TokenValidator.ValidationResult validation = validator.validate(result.getTokenValue());
        
        assertFalse(validation.isValid());
        assertEquals(TokenValidator.ValidationStatus.EXPIRED, validation.getStatus());
    }

    @Test
    void testRS256TokenValidator_ValidateInvalidToken() {
        RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
        
        TokenValidator.ValidationResult validation = validator.validate("invalid-token-12345");
        
        assertFalse(validation.isValid());
        assertEquals(TokenValidator.ValidationStatus.MALFORMED, validation.getStatus());
    }

    @Test
    void testRS256TokenValidator_ExtractUserId() {
        RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
        RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
        
        TokenGenerationResult result = generator.generate(
                "user-001", 
                "test-subject", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        String userId = validator.extractUserId(result.getTokenValue());
        
        assertEquals("user-001", userId);
    }

    @Test
    void testRS256TokenValidator_ExtractJwtId() {
        RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
        RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
        
        TokenGenerationResult result = generator.generate(
                "user-001", 
                "test-subject", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        String jwtId = validator.extractJwtId(result.getTokenValue());
        
        assertEquals(result.getJwtId(), jwtId);
    }

    @Test
    void testHS256TokenGenerator_GenerateToken() {
        HS256TokenGenerator generator = new HS256TokenGenerator(jwtKeyManager);
        
        TokenGenerationResult result = generator.generate(
                "user-002", 
                "hs256-test", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        assertNotNull(result);
        assertNotNull(result.getTokenValue());
        assertFalse(result.getTokenValue().isEmpty());
        assertNotNull(result.getJwtId());
        assertEquals("HS256", generator.getAlgorithm());
    }

    @Test
    void testHS256TokenValidator_ValidateValidToken() {
        HS256TokenGenerator generator = new HS256TokenGenerator(jwtKeyManager);
        HS256TokenValidator validator = new HS256TokenValidator(jwtKeyManager);
        
        TokenGenerationResult result = generator.generate(
                "user-002", 
                "hs256-test", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        TokenValidator.ValidationResult validation = validator.validate(result.getTokenValue());
        
        assertTrue(validation.isValid());
        assertEquals(TokenValidator.ValidationStatus.VALID, validation.getStatus());
        assertNotNull(validation.getClaims());
        assertEquals("user-002", validation.getClaims().get("userId"));
    }

    @Test
    void testHS256TokenValidator_ValidateExpiredToken() {
        HS256TokenGenerator generator = new HS256TokenGenerator(jwtKeyManager);
        HS256TokenValidator validator = new HS256TokenValidator(jwtKeyManager);
        
        TokenGenerationResult result = generator.generate(
                "user-002", 
                "hs256-test", 
                LocalDateTime.now().minusHours(2), 
                LocalDateTime.now().minusHours(1)
        );
        
        TokenValidator.ValidationResult validation = validator.validate(result.getTokenValue());
        
        assertFalse(validation.isValid());
        assertEquals(TokenValidator.ValidationStatus.EXPIRED, validation.getStatus());
    }

    @Test
    void testSimpleTokenGenerator_GenerateToken() {
        SimpleTokenGenerator generator = new SimpleTokenGenerator(tokenProperties);
        
        TokenGenerationResult result = generator.generate(
                "user-003", 
                "simple-test", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        assertNotNull(result);
        assertNotNull(result.getTokenValue());
        assertFalse(result.getTokenValue().isEmpty());
        assertNotNull(result.getJwtId());
        assertEquals("SIMPLE", generator.getAlgorithm());
        assertTrue(result.getTokenValue().startsWith("TOKEN-"));
    }

    @Test
    void testSimpleTokenValidator_ValidateValidToken() {
        SimpleTokenGenerator generator = new SimpleTokenGenerator(tokenProperties);
        SimpleTokenValidator validator = new SimpleTokenValidator(tokenProperties);
        
        TokenGenerationResult result = generator.generate(
                "user-003", 
                "simple-test", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        TokenValidator.ValidationResult validation = validator.validate(result.getTokenValue());
        
        assertTrue(validation.isValid());
        assertEquals(TokenValidator.ValidationStatus.VALID, validation.getStatus());
        assertNotNull(validation.getClaims());
        assertEquals("user-003", validation.getClaims().get("userId"));
    }

    @Test
    void testSimpleTokenValidator_ValidateInvalidToken() {
        SimpleTokenValidator validator = new SimpleTokenValidator(tokenProperties);
        
        TokenValidator.ValidationResult validation = validator.validate("invalid-simple-token");
        
        assertFalse(validation.isValid());
        assertEquals(TokenValidator.ValidationStatus.MALFORMED, validation.getStatus());
    }

    @Test
    void testSimpleTokenValidator_ValidateWrongFormatToken() {
        SimpleTokenValidator validator = new SimpleTokenValidator(tokenProperties);
        
        TokenValidator.ValidationResult validation = validator.validate("INVALID-12345");
        
        assertFalse(validation.isValid());
        assertEquals(TokenValidator.ValidationStatus.MALFORMED, validation.getStatus());
    }

    @Test
    void testSimpleTokenValidator_ExtractUserId() {
        SimpleTokenGenerator generator = new SimpleTokenGenerator(tokenProperties);
        SimpleTokenValidator validator = new SimpleTokenValidator(tokenProperties);
        
        TokenGenerationResult result = generator.generate(
                "user-003", 
                "simple-test", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        String userId = validator.extractUserId(result.getTokenValue());
        
        assertEquals("user-003", userId);
    }

    @Test
    void testRS256TokenGenerator_MultipleTokens() {
        RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
        
        TokenGenerationResult result1 = generator.generate(
                "user-100", "test1", LocalDateTime.now(), LocalDateTime.now().plusHours(1)
        );
        TokenGenerationResult result2 = generator.generate(
                "user-101", "test2", LocalDateTime.now(), LocalDateTime.now().plusHours(2)
        );
        
        assertNotEquals(result1.getTokenValue(), result2.getTokenValue());
        assertNotEquals(result1.getJwtId(), result2.getJwtId());
    }

    @Test
    void testHS256TokenGenerator_MultipleTokens() {
        HS256TokenGenerator generator = new HS256TokenGenerator(jwtKeyManager);
        
        TokenGenerationResult result1 = generator.generate(
                "user-200", "test1", LocalDateTime.now(), LocalDateTime.now().plusHours(1)
        );
        TokenGenerationResult result2 = generator.generate(
                "user-201", "test2", LocalDateTime.now(), LocalDateTime.now().plusHours(2)
        );
        
        assertNotEquals(result1.getTokenValue(), result2.getTokenValue());
        assertNotEquals(result1.getJwtId(), result2.getJwtId());
    }

    @Test
    void testRS256TokenValidator_ParseQuietly() {
        RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
        RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
        
        TokenGenerationResult result = generator.generate(
                "user-300", 
                "test-parse", 
                LocalDateTime.now(), 
                LocalDateTime.now().plusHours(1)
        );
        
        io.jsonwebtoken.Claims claims = validator.parseQuietly(result.getTokenValue());
        
        assertNotNull(claims);
        assertEquals("user-300", claims.get("userId"));
    }

    @Test
    void testRS256TokenValidator_ParseQuietly_InvalidToken() {
        RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
        
        io.jsonwebtoken.Claims claims = validator.parseQuietly("invalid-token");
        
        assertNull(claims);
    }
}
