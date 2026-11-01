package com.onze.api.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    @Query("select token.userId from RefreshToken token where token.tokenHash = :hash")
    Optional<UUID> findUserIdByHash(@Param("hash") String hash);

    boolean existsByLegacyGrantHash(String legacyGrantHash);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken token set token.revokedAt = :now where token.familyId = :family and token.revokedAt is null")
    int revokeFamily(@Param("family") UUID family, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken token set token.revokedAt = :now where token.userId = :userId and token.revokedAt is null")
    int revokeUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
