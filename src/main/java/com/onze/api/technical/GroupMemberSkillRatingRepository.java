package com.onze.api.technical;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberSkillRatingRepository extends JpaRepository<GroupMemberSkillRating, UUID> {
    List<GroupMemberSkillRating> findAllByGroupMemberId(UUID groupMemberId);
    List<GroupMemberSkillRating> findAllByGroupMemberIdIn(List<UUID> groupMemberIds);
}
