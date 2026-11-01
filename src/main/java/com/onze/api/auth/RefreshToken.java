package com.onze.api.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_refresh_tokens")
public class RefreshToken {
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "family_id", nullable = false)
    private UUID familyId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "family_expires_at", nullable = false)
    private Instant familyExpiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "legacy_grant_hash", unique = true, length = 64)
    private String legacyGrantHash;

    protected RefreshToken() {}

    public RefreshToken(String tokenHash, UUID userId, UUID familyId, Instant createdAt,
            Instant expiresAt, Instant familyExpiresAt, String legacyGrantHash) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.familyId = familyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.familyExpiresAt = familyExpiresAt;
        this.legacyGrantHash = legacyGrantHash;
    }

    public String getTokenHash() { return tokenHash; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getFamilyExpiresAt() { return familyExpiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void consume(Instant now) { consumedAt = now; }
}
