package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupTeamIdentityRepository extends JpaRepository<GroupTeamIdentity, UUID> {
    List<GroupTeamIdentity> findAllByGroupIdAndMatchTypeOrderByTeamNumberAsc(
            UUID groupId,
            MatchType matchType);

    Optional<GroupTeamIdentity> findByGroupIdAndMatchTypeAndTeamNumber(
            UUID groupId,
            MatchType matchType,
            int teamNumber);
}
