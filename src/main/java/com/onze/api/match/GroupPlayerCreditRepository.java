package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupPlayerCreditRepository extends JpaRepository<GroupPlayerCredit, UUID> {

    List<GroupPlayerCredit> findAllByGroupIdOrderByCreatedAtAsc(UUID groupId);

    Optional<GroupPlayerCredit> findByGroupIdAndUserId(UUID groupId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select credit
            from GroupPlayerCredit credit
            where credit.groupId = :groupId and credit.userId = :userId
            """)
    Optional<GroupPlayerCredit> findByGroupIdAndUserIdForUpdate(
            @Param("groupId") UUID groupId,
            @Param("userId") UUID userId);
}
