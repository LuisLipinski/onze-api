package com.onze.api.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    Optional<PasswordResetCode> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PasswordResetCode> findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("""
            update PasswordResetCode code
               set code.consumedAt = :now
             where code.userId = :userId
               and code.consumedAt is null
            """)
    int consumeActiveCodes(@Param("userId") UUID userId, @Param("now") Instant now);
}
