package com.onze.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.onze.api.auth.RefreshTokenService.InvalidRefreshTokenException;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    @Mock RefreshTokenRepository tokens;
    @Mock UserRepository users;
    @Mock TokenService access;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-01T12:00:00Z"));
    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private final String original = "a".repeat(43);
    private User user;
    private RefreshTokenService service;

    @BeforeEach void setup() {
        user = new User("person@onze.test", "hash", "Jogador");
        ReflectionTestUtils.setField(user, "id", userId);
        SessionClock clock = now::get;
        service = new RefreshTokenService(tokens, users, access, clock, Duration.ofDays(30), Duration.ofDays(90));
    }

    private RefreshToken known(Instant expiry, Instant absolute) {
        RefreshToken token = new RefreshToken(RefreshTokenService.hash(original), userId, familyId,
                now.get().minusSeconds(60), expiry, absolute, null);
        when(tokens.findUserIdByHash(token.getTokenHash())).thenReturn(Optional.of(userId));
        when(users.findByIdForSession(userId)).thenReturn(Optional.of(user));
        when(tokens.findById(token.getTokenHash())).thenReturn(Optional.of(token));
        return token;
    }

    @Test void storesOnlyHashAndCreatesIndependentFamilies() {
        var first = service.issue(user);
        var second = service.issue(user);
        assertTrue(first.value().matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(first.value(), second.value());
        assertEquals(Duration.ofDays(30).toSeconds(), first.expiresInSeconds());
        var saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens, times(2)).save(saved.capture());
        assertEquals(RefreshTokenService.hash(first.value()), saved.getAllValues().get(0).getTokenHash());
        assertNotEquals(first.value(), saved.getAllValues().get(0).getTokenHash());
        assertNotEquals(saved.getAllValues().get(0).getFamilyId(), saved.getAllValues().get(1).getFamilyId());
    }

    @Test void rotatesWithSameAccountAndCapsAbsoluteDeadline() {
        var token = known(now.get().plusSeconds(600), now.get().plusSeconds(1200));
        when(access.issueToken(user)).thenReturn(new TokenService.IssuedToken("new-access", 7200));
        var response = service.refresh(original);
        assertEquals("new-access", response.accessToken());
        assertEquals(userId, response.user().id());
        assertNotEquals(original, response.refreshToken());
        assertEquals(1200, response.refreshExpiresIn());
        assertEquals(now.get(), token.getConsumedAt());
        var saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens, times(2)).save(saved.capture());
        assertEquals(familyId, saved.getAllValues().get(1).getFamilyId());
        assertEquals(token.getFamilyExpiresAt(), saved.getAllValues().get(1).getExpiresAt());
    }

    @Test void replayRevokesWholeFamilyAndIssuesNothing() {
        var token = known(now.get().plusSeconds(600), now.get().plusSeconds(1200));
        token.consume(now.get().minusSeconds(1));
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(original));
        verify(tokens).revokeFamily(familyId, now.get());
        verifyNoInteractions(access);
    }

    @Test void idleExpiryRejectsAtExactDeadline() {
        known(now.get(), now.get().plus(Duration.ofDays(60)));
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(original));
        verify(tokens).revokeFamily(familyId, now.get());
        verifyNoInteractions(access);
    }

    @Test void absoluteExpiryRejectsEvenIfChildHasLaterExpiry() {
        known(now.get().plusSeconds(600), now.get());
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(original));
        verify(tokens).revokeFamily(familyId, now.get());
    }

    @Test void revokedTokenCannotRenew() {
        var token = known(now.get().plusSeconds(600), now.get().plusSeconds(1200));
        ReflectionTestUtils.setField(token, "revokedAt", now.get().minusSeconds(1));
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(original));
        verifyNoInteractions(access);
    }

    @Test void unknownTokenCannotRenew() {
        when(tokens.findUserIdByHash(any())).thenReturn(Optional.empty());
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(original));
        verifyNoInteractions(users, access);
    }

    @Test void deletedAccountCannotRenew() {
        when(tokens.findUserIdByHash(any())).thenReturn(Optional.of(userId));
        when(users.findByIdForSession(userId)).thenReturn(Optional.empty());
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(original));
        verifyNoInteractions(access);
    }

    @Test void logoutRevokesEvenConsumedTokenFamily() {
        var token = known(now.get().plusSeconds(600), now.get().plusSeconds(1200));
        token.consume(now.get());
        service.revoke(original);
        verify(tokens).revokeFamily(familyId, now.get());
    }

    @Test void unknownLogoutIsIdempotent() {
        when(tokens.findUserIdByHash(any())).thenReturn(Optional.empty());
        service.revoke(original);
        verifyNoInteractions(users, access);
    }

    private Jwt legacy() {
        return Jwt.withTokenValue("legacy").header("alg", "HS256").issuer("https://onze.test")
                .subject(userId.toString()).issuedAt(now.get().minusSeconds(60))
                .expiresAt(now.get().plusSeconds(7200)).build();
    }

    @Test void migratesLegacyAccessOnceWithoutPassword() {
        when(users.findByIdForSession(userId)).thenReturn(Optional.of(user));
        when(access.issueToken(user)).thenReturn(new TokenService.IssuedToken("new-access", 7200));
        var response = service.exchangeLegacySession(legacy());
        assertEquals(userId, response.user().id());
        assertNotNull(response.refreshToken());
        verify(tokens).existsByLegacyGrantHash(any());
    }

    @Test void rejectsRepeatedLegacyExchange() {
        when(users.findByIdForSession(userId)).thenReturn(Optional.of(user));
        when(tokens.existsByLegacyGrantHash(any())).thenReturn(true);
        assertThrows(InvalidRefreshTokenException.class, () -> service.exchangeLegacySession(legacy()));
        verify(tokens, never()).save(any());
    }

    @Test void newAccessTokensCannotBootstrapMoreSessions() {
        Jwt jwt = Jwt.withTokenValue("new").header("alg", "HS256").issuer("https://onze.test")
                .subject(userId.toString()).claim("jti", UUID.randomUUID().toString()).issuedAt(now.get())
                .expiresAt(now.get().plusSeconds(7200)).build();
        assertThrows(InvalidRefreshTokenException.class, () -> service.exchangeLegacySession(jwt));
        verifyNoInteractions(tokens, users, access);
    }

    @Test void passwordChangeInvalidatesOldLegacyGrant() {
        user.changePasswordHash("new-password");
        ReflectionTestUtils.setField(user, "passwordChangedAt", now.get());
        when(users.findByIdForSession(userId)).thenReturn(Optional.of(user));
        assertThrows(InvalidRefreshTokenException.class, () -> service.exchangeLegacySession(legacy()));
        verify(tokens, never()).save(any());
    }
}
