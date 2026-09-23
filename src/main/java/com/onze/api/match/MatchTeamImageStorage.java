package com.onze.api.match;

import java.util.UUID;

public interface MatchTeamImageStorage {
    String upload(UUID matchId, int teamNumber, byte[] content);
}
