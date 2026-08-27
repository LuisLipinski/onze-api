package com.onze.api.group;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupScheduleRepository extends JpaRepository<GroupSchedule, UUID> {

    List<GroupSchedule> findAllByGroupId(UUID groupId);

    void deleteAllByGroupId(UUID groupId);
}
