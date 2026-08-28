package com.onze.api.match;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchNotificationJobRepository extends JpaRepository<MatchNotificationJob, UUID> {

    List<MatchNotificationJob> findTop25ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            MatchNotificationStatus status,
            Instant nextAttemptAt);
}
