package com.onze.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.auth.AuthModels.UserResponse;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final TokenService accessTokens;
    private final Clock clock;
    private final Duration idleTtl;
    private final Duration absoluteTtl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, TokenService accessTokens,
            Clock clock, @Value("${security.session.refresh-idle-ttl:P30D}") Duration idleTtl,
            @Value("${security.session.refresh-absolute-ttl:P90D}") Duration absoluteTtl) {
        if (idleTtl.isNegative() || idleTtl.isZero() || absoluteTtl.isNegative() || absoluteTtl.isZero()) {
            throw new IllegalArgumentException("Refresh token lifetimes must be positive");
        }
        this.tokens = tokens;
        this.users = users;
        this.accessTokens = accessTokens;
        this.clock = clock;
        this.idleTtl = idleTtl;
        this.absoluteTtl = absoluteTtl;
    }

    @Transactional
    public IssuedRefresh issue(User user) {
        return create(user.getId(), UUID.randomUUID(), clock.instant().plus(absoluteTtl), null);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String value) {
        String hash = hash(value);
        UUID userId = tokens.findUserIdByHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        // Lock the account before loading the token entity. Concurrent refreshes,
        // logout and password reset then observe the last committed rotation.
        User user = users.findByIdForSession(userId).orElseThrow(InvalidRefreshTokenException::new);
        RefreshToken token = tokens.findById(hash).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (token.getConsumedAt() != null || token.getRevokedAt() != null
                || !token.getExpiresAt().isAfter(now) || !token.getFamilyExpiresAt().isAfter(now)) {
            tokens.revokeFamily(token.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        token.consume(now);
        tokens.save(token);
        return response(user, create(userId, token.getFamilyId(), token.getFamilyExpiresAt(), null));
    }

    @Transactional
    public void revoke(String value) {
        String hash = hash(value);
        var userId = tokens.findUserIdByHash(hash);
        if (userId.isEmpty() || users.findByIdForSession(userId.get()).isEmpty()) return;
        tokens.findById(hash).ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        tokens.revokeUser(userId, clock.instant());
    }

    @Transactional
    public AuthResponse exchangeLegacySession(Jwt jwt) {
        // Existing installations stored an access JWT only. Exchange it once,
        // while it is still valid; newly issued JWTs have jti and cannot use this.
        if (jwt.getId() != null || jwt.getSubject() == null || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
            throw new InvalidRefreshTokenException();
        }
        UUID userId;
        try { userId = UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) { throw new InvalidRefreshTokenException(); }
        User user = users.findByIdForSession(userId).orElseThrow(InvalidRefreshTokenException::new);
        if (user.getPasswordChangedAt() != null && !jwt.getIssuedAt().isAfter(user.getPasswordChangedAt())) {
            throw new InvalidRefreshTokenException();
        }
        String grant = hash(jwt.getClaimAsString("iss") + ":" + userId + ":" + jwt.getIssuedAt().getEpochSecond()
                + ":" + jwt.getExpiresAt().getEpochSecond());
        if (tokens.existsByLegacyGrantHash(grant)) throw new InvalidRefreshTokenException();
        return response(user, create(userId, UUID.randomUUID(), clock.instant().plus(absoluteTtl), grant));
    }

    private IssuedRefresh create(UUID userId, UUID familyId, Instant familyExpiresAt, String legacyGrant) {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(idleTtl);
        if (expiresAt.isAfter(familyExpiresAt)) expiresAt = familyExpiresAt;
        tokens.save(new RefreshToken(hash(value), userId, familyId, now, expiresAt, familyExpiresAt, legacyGrant));
        return new IssuedRefresh(value, Duration.between(now, expiresAt).toSeconds());
    }

    private AuthResponse response(User user, IssuedRefresh refresh) {
        var access = accessTokens.issueToken(user);
        return new AuthResponse(access.value(), "Bearer", access.expiresInSeconds(), UserResponse.from(user),
                refresh.value(), refresh.expiresInSeconds());
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record IssuedRefresh(String value, long expiresInSeconds) {}
    public static final class InvalidRefreshTokenException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
