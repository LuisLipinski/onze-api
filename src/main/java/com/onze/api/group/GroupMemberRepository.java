package com.onze.api.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    List<GroupMember> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    List<GroupMember> findAllByGroupIdOrderByCreatedAtAsc(UUID groupId);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);
}
