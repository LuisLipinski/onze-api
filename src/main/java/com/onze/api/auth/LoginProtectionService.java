package com.onze.api.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.onze.api.user.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginProtectionService {

    private final Clock clock;
    private final int maxAttempts;
    private final Duration observationWindow;
    private final Duration initialBlockDuration;
    private final Duration maxBlockDuration;

    public LoginProtectionService(
            Clock clock,
            @Value("${security.login.max-attempts:5}") int maxAttempts,
            @Value("${security.login.observation-window:PT15M}") Duration observationWindow,
            @Value("${security.login.initial-block-duration:PT1M}") Duration initialBlockDuration,
            @Value("${security.login.max-block-duration:PT15M}") Duration maxBlockDuration) {
        if (maxAttempts < 1
                || observationWindow.isZero()
                || observationWindow.isNegative()
                || initialBlockDuration.isZero()
                || initialBlockDuration.isNegative()
                || maxBlockDuration.compareTo(initialBlockDuration) < 0) {
            throw new IllegalArgumentException("Invalid login protection configuration");
        }
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.observationWindow = observationWindow;
        this.initialBlockDuration = initialBlockDuration;
        this.maxBlockDuration = maxBlockDuration;
    }

    void requireLoginAllowed(User user) {
        Instant now = Instant.now(clock);
        if (user.getLoginBlockedUntil() != null && user.getLoginBlockedUntil().isAfter(now)) {
            throw rateLimitExceeded(now, user.getLoginBlockedUntil());
        }
    }

    void registerFailedAttempt(User user) {
        Instant now = Instant.now(clock);
        Instant windowStartedAt = user.getLoginAttemptWindowStartedAt();
        int failedAttempts = user.getFailedLoginAttempts();

        if (windowStartedAt == null
                || !windowStartedAt.plus(observationWindow).isAfter(now)) {
            windowStartedAt = now;
            failedAttempts = 0;
        }

        failedAttempts++;
        Instant blockedUntil = null;
        if (failedAttempts >= maxAttempts) {
            int exponent = Math.min(failedAttempts - maxAttempts, 30);
            long multiplier = 1L << exponent;
            Duration blockDuration = initialBlockDuration.multipliedBy(multiplier);
            if (blockDuration.compareTo(maxBlockDuration) > 0) {
                blockDuration = maxBlockDuration;
            }
            blockedUntil = now.plus(blockDuration);
        }

        user.updateLoginProtection(failedAttempts, windowStartedAt, blockedUntil);
        if (blockedUntil != null) {
            throw rateLimitExceeded(now, blockedUntil);
        }
    }

    void clear(User user) {
        user.clearLoginProtection();
    }

    private LoginRateLimitExceededException rateLimitExceeded(Instant now, Instant blockedUntil) {
        long retryAfterSeconds = Math.max(1, Duration.between(now, blockedUntil).toSeconds());
        return new LoginRateLimitExceededException(retryAfterSeconds);
    }

    public static final class LoginRateLimitExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final long retryAfterSeconds;

        LoginRateLimitExceededException(long retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
