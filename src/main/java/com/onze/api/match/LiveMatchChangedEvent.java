package com.onze.api.match;

import java.util.UUID;

public record LiveMatchChangedEvent(
        UUID matchId,
        UUID groupId,
        long version,
        LiveMatchChangeType type) {
}
