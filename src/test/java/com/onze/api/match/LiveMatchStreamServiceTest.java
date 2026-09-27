package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRole;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.MatchService.MatchNotFoundException;

import org.junit.jupiter.api.Test;

class LiveMatchStreamServiceTest {

    @Test
    void subscribesOnceAndKeepsHeartbeatOutOfTheDatabase() {
        GroupMemberRepository members = mock(GroupMemberRepository.class);
        FootballMatchRepository matches = mock(FootballMatchRepository.class);
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        when(members.findAllByUserIdOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(new GroupMember(groupId, userId, GroupRole.MEMBER)));
        LiveMatchStreamService service = new LiveMatchStreamService(members, matches);

        service.subscribe(userId.toString(), null);
        service.heartbeat();

        assertEquals(1, service.activeConnectionCount());
        assertEquals(1, service.activeFeedConnectionCount());
        verify(members, times(1)).findAllByUserIdOrderByCreatedAtAsc(userId);
        verifyNoInteractions(matches);
    }

    @Test
    void keepsOnlyTheRequestedMatchPermissionInAMatchSubscription() {
        GroupMemberRepository members = mock(GroupMemberRepository.class);
        FootballMatchRepository matches = mock(FootballMatchRepository.class);
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        FootballMatch match = mock(FootballMatch.class);
        when(matches.findById(matchId)).thenReturn(Optional.of(match));
        when(match.getGroupId()).thenReturn(groupId);
        when(members.findByGroupIdAndUserId(groupId, userId))
                .thenReturn(Optional.of(new GroupMember(groupId, userId, GroupRole.MEMBER)));
        LiveMatchStreamService service = new LiveMatchStreamService(members, matches);

        service.subscribe(userId.toString(), matchId);

        assertEquals(1, service.activeConnectionCount());
        assertEquals(1, service.activeConnectionCount(matchId));
        assertEquals(0, service.activeFeedConnectionCount());
        verify(members, never()).findAllByUserIdOrderByCreatedAtAsc(userId);
    }

    @Test
    void rejectsAMatchSubscriptionFromAUserOutsideTheGroup() {
        GroupMemberRepository members = mock(GroupMemberRepository.class);
        FootballMatchRepository matches = mock(FootballMatchRepository.class);
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        FootballMatch match = mock(FootballMatch.class);
        when(matches.findById(matchId)).thenReturn(Optional.of(match));
        when(match.getGroupId()).thenReturn(groupId);
        when(members.findByGroupIdAndUserId(groupId, userId)).thenReturn(Optional.empty());
        LiveMatchStreamService service = new LiveMatchStreamService(members, matches);

        assertThrows(
                GroupAccessDeniedException.class,
                () -> service.subscribe(userId.toString(), matchId));
        assertEquals(0, service.activeConnectionCount());
    }

    @Test
    void rejectsAMatchSubscriptionForAnUnknownMatch() {
        FootballMatchRepository matches = mock(FootballMatchRepository.class);
        UUID matchId = UUID.randomUUID();
        when(matches.findById(matchId)).thenReturn(Optional.empty());
        LiveMatchStreamService service = new LiveMatchStreamService(
                mock(GroupMemberRepository.class),
                matches);

        assertThrows(
                MatchNotFoundException.class,
                () -> service.subscribe(UUID.randomUUID().toString(), matchId));
        assertEquals(0, service.activeConnectionCount());
    }

    @Test
    void rejectsInvalidAuthenticatedUserId() {
        LiveMatchStreamService service = new LiveMatchStreamService(
                mock(GroupMemberRepository.class),
                mock(FootballMatchRepository.class));

        assertThrows(GroupUserNotFoundException.class, () -> service.subscribe("invalid", null));
    }
}
