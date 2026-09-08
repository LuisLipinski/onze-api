package com.onze.api.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {

    Optional<PushDevice> findByExpoPushToken(String expoPushToken);

    List<PushDevice> findAllByUserIdInAndActiveTrue(Collection<UUID> userIds);
}
