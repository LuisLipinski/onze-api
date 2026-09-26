package com.onze.api.match;

import java.util.UUID;

public interface MatchTeamImageStorage {
    String upload(UUID groupId, MatchType matchType, int teamNumber, byte[] content);
}
