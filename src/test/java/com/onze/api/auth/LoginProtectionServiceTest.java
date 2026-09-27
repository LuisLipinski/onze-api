package com.onze.api.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.onze.api.auth.LoginProtectionService.LoginRateLimitExceededException;
import com.onze.api.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginProtectionServiceTest {

    private static final Instant START = Instant.parse("2026-09-27T12:00:00Z");

    @Mock
    private Clock clock;

    private LoginProtectionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new LoginProtectionService(
                clock,
                5,
                Duration.ofMinutes(15),
                Duration.ofMinutes(1),
                Duration.ofMinutes(15));
        user = new User("player@example.com", "password-hash", "Jogador");
        when(clock.instant()).thenReturn(START);
    }

    @Test
    void blocksOnFifthFailureAndIncreasesTheNextCooldown() {
        for (int attempt = 1; attempt < 5; attempt++) {
            assertDoesNotThrow(() -> service.registerFailedAttempt(user));
        }

        LoginRateLimitExceededException firstBlock = assertThrows(
                LoginRateLimitExceededException.class,
                () -> service.registerFailedAttempt(user));
        assertEquals(60, firstBlock.getRetryAfterSeconds());
        assertEquals(5, user.getFailedLoginAttempts());
        assertEquals(START.plusSeconds(60), user.getLoginBlockedUntil());

        when(clock.instant()).thenReturn(START.plusSeconds(61));
        LoginRateLimitExceededException secondBlock = assertThrows(
                LoginRateLimitExceededException.class,
                () -> service.registerFailedAttempt(user));
        assertEquals(120, secondBlock.getRetryAfterSeconds());
        assertEquals(6, user.getFailedLoginAttempts());
    }

    @Test
    void rejectsAttemptsWhileCooldownIsActive() {
        for (int attempt = 1; attempt < 5; attempt++) {
            service.registerFailedAttempt(user);
        }
        assertThrows(LoginRateLimitExceededException.class, () -> service.registerFailedAttempt(user));

        when(clock.instant()).thenReturn(START.plusSeconds(30));
        LoginRateLimitExceededException exception = assertThrows(
                LoginRateLimitExceededException.class,
                () -> service.requireLoginAllowed(user));

        assertEquals(30, exception.getRetryAfterSeconds());
    }

    @Test
    void restartsCounterAfterObservationWindow() {
        service.registerFailedAttempt(user);
        service.registerFailedAttempt(user);

        Instant afterWindow = START.plus(Duration.ofMinutes(16));
        when(clock.instant()).thenReturn(afterWindow);
        assertDoesNotThrow(() -> service.registerFailedAttempt(user));

        assertEquals(1, user.getFailedLoginAttempts());
        assertEquals(afterWindow, user.getLoginAttemptWindowStartedAt());
        assertNull(user.getLoginBlockedUntil());
    }

    @Test
    void clearsProtectionAfterSuccessfulAuthentication() {
        service.registerFailedAttempt(user);

        service.clear(user);

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLoginAttemptWindowStartedAt());
        assertNull(user.getLoginBlockedUntil());
    }
}
