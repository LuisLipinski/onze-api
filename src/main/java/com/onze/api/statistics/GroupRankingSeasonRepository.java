package com.onze.api.statistics;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRankingSeasonRepository extends JpaRepository<GroupRankingSeason, UUID> {
    List<GroupRankingSeason> findAllByGroupIdOrderByNumberDesc(UUID groupId);
}
