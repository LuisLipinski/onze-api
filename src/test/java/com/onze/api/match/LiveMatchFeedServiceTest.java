package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.onze.api.group.Group;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveMatchFeedServiceTest {
    private FootballMatchRepository matches;
    private GroupMemberRepository members;
    private GroupRepository groups;
    private LiveMatchScoreRepository scores;
    private LiveMatchFeedService service;

    @BeforeEach
    void setUp() {
        matches = mock(FootballMatchRepository.class);
        members = mock(GroupMemberRepository.class);
        groups = mock(GroupRepository.class);
        scores = mock(LiveMatchScoreRepository.class);
        service = new LiveMatchFeedService(matches, members, groups, scores);
    }

    @Test
    void listsOnlyLiveMatchesFromTheUsersGroupsWithCurrentScore() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        GroupMember membership = new GroupMember(groupId, userId, GroupRole.MEMBER);
        FootballMatch match = mock(FootballMatch.class);
        Group group = mock(Group.class);
        LiveMatchScore score = mock(LiveMatchScore.class);
        Instant startsAt = Instant.parse("2026-09-22T18:00:00Z");
        Instant startedAt = Instant.parse("2026-09-22T18:02:00Z");

        when(match.getId()).thenReturn(matchId);
        when(match.getGroupId()).thenReturn(groupId);
        when(match.getStartsAt()).thenReturn(startsAt);
        when(match.getStartedAt()).thenReturn(startedAt);
        when(match.getTimeZone()).thenReturn("America/Sao_Paulo");
        when(match.getVenue()).thenReturn("Arena Onze");
        when(match.getStatus()).thenReturn(MatchStatus.IN_PROGRESS);
        when(match.getMatchType()).thenReturn(MatchType.INTERNAL);
        when(match.getTeamCount()).thenReturn(2);
        when(match.getLiveVersion()).thenReturn(7L);
        when(group.getId()).thenReturn(groupId);
        when(group.getName()).thenReturn("Time de terça");
        when(score.getMatchId()).thenReturn(matchId);
        when(score.getSideNumber()).thenReturn(1);
        when(score.getScore()).thenReturn(3);
        when(members.findAllByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(membership));
        when(matches.findAllByGroupIdInAndStatusInOrderByStartsAtAsc(
                Set.of(groupId), List.of(MatchStatus.IN_PROGRESS))).thenReturn(List.of(match));
        when(groups.findAllById(Set.of(groupId))).thenReturn(List.of(group));
        when(scores.findAllByMatchIdInOrderByMatchIdAscSideNumberAsc(List.of(matchId)))
                .thenReturn(List.of(score));

        var result = service.list(userId.toString());

        assertEquals(1, result.size());
        assertEquals(matchId, result.getFirst().matchId());
        assertEquals("Time de terça", result.getFirst().groupName());
        assertEquals(3, result.getFirst().scores().getFirst().score());
        assertEquals(7L, result.getFirst().version());
        assertEquals(MatchStatus.IN_PROGRESS, result.getFirst().status());
        assertFalse(result.getFirst().canManage());
    }

    @Test
    void avoidsLiveQueriesWhenUserHasNoGroups() {
        UUID userId = UUID.randomUUID();
        when(members.findAllByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

        assertEquals(List.of(), service.list(userId.toString()));
        verifyNoInteractions(matches, groups, scores);
    }
}
