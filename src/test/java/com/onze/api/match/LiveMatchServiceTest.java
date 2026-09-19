package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRole;
import com.onze.api.match.LiveMatchService.InvalidLiveMatchTransitionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveMatchServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-19T18:00:00Z");
    private FootballMatchRepository matches;
    private GroupMemberRepository members;
    private LiveMatchService service;
    private UUID matchId;
    private UUID groupId;
    private UUID adminId;
    private FootballMatch match;

    @BeforeEach
    void setUp() {
        matches = mock(FootballMatchRepository.class);
        members = mock(GroupMemberRepository.class);
        service = new LiveMatchService(matches, members, Clock.fixed(NOW, ZoneOffset.UTC));
        matchId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        match = new FootballMatch(groupId, null, null, NOW.plusSeconds(3600), "UTC", "Arena", 14,
                MatchType.INTERNAL, 2, 2, MatchModality.FUT7, 14, null, null, true, null,
                NOW.minusSeconds(3600), NOW.minusSeconds(3600), NOW.plusSeconds(1800), null, adminId);
        when(matches.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(members.findByGroupIdAndUserId(groupId, adminId))
                .thenReturn(Optional.of(new GroupMember(groupId, adminId, GroupRole.PRIMARY_ADMIN)));
    }

    @Test
    void startsAndFinishesMatchWithAuditTimestamps() {
        service.start(adminId.toString(), matchId);
        assertEquals(MatchStatus.IN_PROGRESS, match.getStatus());
        assertEquals(NOW, match.getStartedAt());

        service.finish(adminId.toString(), matchId);
        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(NOW, match.getFinishedAt());
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.finish(adminId.toString(), matchId));
        service.start(adminId.toString(), matchId);
        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.start(adminId.toString(), matchId));
    }
}
