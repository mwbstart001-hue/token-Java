package com.example.tokenservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private MemoryUserService userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "authEnabled", false);
        userService.init();
    }

    @Test
    void authenticate_ShouldReturnTrue_WhenAuthDisabled() {
        boolean result = userService.authenticate("any-user", "any-password");

        assertTrue(result);
    }

    @Test
    void authenticate_ShouldReturnTrue_WhenValidCredentials() {
        ReflectionTestUtils.setField(userService, "authEnabled", true);
        userService.init();

        boolean result = userService.authenticate("user-123", "password123");

        assertTrue(result);
    }

    @Test
    void authenticate_ShouldReturnFalse_WhenInvalidPassword() {
        ReflectionTestUtils.setField(userService, "authEnabled", true);
        userService.init();

        boolean result = userService.authenticate("user-123", "wrong-password");

        assertFalse(result);
    }

    @Test
    void authenticate_ShouldReturnFalse_WhenUserNotFound() {
        ReflectionTestUtils.setField(userService, "authEnabled", true);
        userService.init();

        boolean result = userService.authenticate("non-existent-user", "password123");

        assertFalse(result);
    }

    @Test
    void authenticate_ShouldReturnFalse_WhenUserIdIsNull() {
        ReflectionTestUtils.setField(userService, "authEnabled", true);
        userService.init();

        boolean result = userService.authenticate(null, "password123");

        assertFalse(result);
    }

    @Test
    void authenticate_ShouldReturnFalse_WhenPasswordIsNull() {
        ReflectionTestUtils.setField(userService, "authEnabled", true);
        userService.init();

        boolean result = userService.authenticate("user-123", null);

        assertFalse(result);
    }

    @Test
    void getUsername_ShouldReturnUsername_WhenUserExists() {
        String username = userService.getUsername("user-123");

        assertEquals("test-user", username);
    }

    @Test
    void getUsername_ShouldReturnUserId_WhenUserNotFound() {
        String username = userService.getUsername("non-existent-user");

        assertEquals("non-existent-user", username);
    }

    @Test
    void getUsername_ShouldReturnNull_WhenUserIdIsNull() {
        String username = userService.getUsername(null);

        assertNull(username);
    }
}
