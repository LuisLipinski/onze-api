package com.onze.api.group;

import java.util.UUID;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select groupEntity from Group groupEntity where groupEntity.id = :id")
    Optional<Group> findByIdForUpdate(@Param("id") UUID id);
}
